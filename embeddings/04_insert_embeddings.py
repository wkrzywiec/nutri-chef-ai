import json
import logging
import psycopg2
import tiktoken
from typing import List, Dict, Any
from pathlib import Path
from datetime import datetime

embedding_files = ["", ""]

# Configuration options
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "database": "meal_planner",
    "user": "postgres",
    "password": "postgres"
}
EMBEDDING_MODEL = "text-embedding-3-small"


# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

_loaded_request_data = None

def get_token_count(text: Any, model: str) -> int:
    if text is None:
        text = ""
    if not isinstance(text, str):
        text = str(text)
    
    text = text.replace('\x00', '')
    
    enc = tiktoken.encoding_for_model(model)
    try:
        token_count = len(enc.encode(text))
        logger.debug(f"Token count: {token_count:,} for text of length {len(text):,}")
        return token_count
    except Exception as e:
        logger.error(f"Error counting tokens for text: {text[:100]}... Error: {str(e)}")
        return 0

def load_request_data():
    global _loaded_request_data
    if _loaded_request_data is None:
        _loaded_request_data = []
        with open('batch-files/recipes_for_embedding.jsonl', 'r', encoding='utf-8') as file:
            for line in file:
                _loaded_request_data.append(json.loads(line))

def find_content(custom_id):
    load_request_data()
    
    # Search for matching custom_id
    for item in _loaded_request_data:
        if item.get('custom_id') == custom_id:
            return item.get('body', {}).get('input')
    
    logger.warning(f"Content not found for custom_id: {custom_id}")
    return None

def load_embeddings(files: List[str]) -> List[Dict[str, Any]]:
    all_embeddings = []
    for file in files:
        file_path = Path("batch-files") / file
        if not file_path.exists():
            logger.error(f"Embedding file not found: {file_path}")
            continue
        
        logger.info(f"Loading embeddings from file: {file_path}")
        with open(file_path, "r", encoding='utf-8') as f:
            for line in f:
                try:
                    record = json.loads(line.strip())
                    custom_id = record.get("custom_id", {})
                    recipe_id, chunk_type = custom_id.split('_', 1)

                    embedding = record.get("response", []).get("body", []).get("data", [])[0].get("embedding", [])
                    content = find_content(custom_id)
                    token_count = get_token_count(content, EMBEDDING_MODEL)
                    
                    all_embeddings.append({
                        "recipe_id": recipe_id,
                        "chunk_type": chunk_type,
                        "embedding": embedding,
                        "token_count": token_count
                    })
                        
                except json.JSONDecodeError as e:
                    logger.error(f"JSON parsing error in {file_path}: {str(e)}")
                except Exception as e:
                    logger.error(f"Error processing line in {file_path}: {str(e)}")
    
    logger.info(f"Loaded total of {len(all_embeddings)} embeddings from all files")
    return all_embeddings


def get_db_connection():
    logger.info("Establishing database connection...")
    conn = psycopg2.connect(**DB_CONFIG)
    logger.info("Database connection established successfully")
    return conn


def store_embeddings(conn, embeddings: List[Dict[str, Any]]):
    logger.info("Starting to store embeddings in database")
    with conn.cursor() as cur:
        for idx, emb in enumerate(embeddings, 1):
            cur.execute("""
                INSERT INTO recipe_embeddings (recipe_id, chunk_type, embedding, token_count)
                VALUES (%s, %s, %s, %s)
            """, (emb["recipe_id"], emb["chunk_type"], emb["embedding"], emb["token_count"]))
            
            if idx % 100 == 0:  # Log progress for every 100 items
                logger.info(f"Stored {idx}/{len(embeddings)} embeddings")
                conn.commit()  # Intermediate commit every 100 records
                
        conn.commit()  # Final commit for remaining records
    logger.info(f"Successfully stored all {len(embeddings)} embeddings in database")

def main():
    try:

        all_embeddings = load_embeddings(embedding_files)
        # Connect to database and fetch recipes
        conn = get_db_connection()

    
        # Store all embeddings
        if all_embeddings:
            logger.info(f"Storing total of {len(all_embeddings)} embeddings from all files")
            store_embeddings(conn, all_embeddings)
        else:
            logger.error("No embeddings were generated")
    
    except Exception as e:
            logger.error(f"Error during embedding generation: {str(e)}")
            raise
    finally:
        # Cleanup
        logger.info("Performing cleanup")
        if 'conn' in locals():
            conn.close()
            logger.info("Database connection closed")


if __name__ == "__main__":
    main()