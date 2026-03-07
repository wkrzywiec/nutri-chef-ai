import json
import os
from pathlib import Path
import logging
from typing import Any, Dict, List
import psycopg2
import openai
import tiktoken     
from datetime import datetime
from dotenv import load_dotenv

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

load_dotenv()

# Configuration options
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "database": "meal_planner",
    "user": "postgres",
    "password": "postgres"
}
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
EMBEDDING_MODEL = "text-embedding-3-small"
MAX_RECORDS_PER_BATCH_JSONL_FILE = 20_000

CHUNK_TYPES = ["name", "description", "ingredients", "instructions", "tags"]

def get_db_connection():
    logger.info("Establishing database connection...")
    conn = psycopg2.connect(**DB_CONFIG, client_encoding='UTF8')
    
    # Ensure proper handling of Polish characters
    with conn.cursor() as cur:
        cur.execute("SET client_encoding TO 'UTF8'")
        cur.execute("SET names 'UTF8'")
    
    logger.info("Database connection established successfully with UTF-8 encoding")
    return conn

def fetch_recipes(conn) -> List[Dict[str, Any]]:
    logger.info("Fetching recipes from database...")
    with conn.cursor() as cur:
        cur.execute("SELECT id, name, description, ingredients, instructions, tags FROM recipe")
        columns = [desc[0] for desc in cur.description]
        recipes = [dict(zip(columns, row)) for row in cur.fetchall()]
        logger.info(f"Retrieved {len(recipes)} recipes from database")
        return recipes

def chunk_recipe(recipe: Dict[str, Any]) -> List[Dict[str, Any]]:
    logger.debug(f"Chunking recipe {recipe['id']}...")
    chunks = []
    for chunk_type in CHUNK_TYPES:
        content = recipe.get(chunk_type)
        if content:
            chunks.append({
                "recipe_id": recipe["id"],
                "chunk_type": chunk_type,
                "content": content
            })
    logger.debug(f"Created {len(chunks)} chunks for recipe {recipe['id']}")
    return chunks

def get_token_count(text: Any, model: str) -> int:
    # Ensure text is a string
    if text is None:
        text = ""
    if not isinstance(text, str):
        text = str(text)
    
    # Remove any null bytes that might cause issues
    text = text.replace('\x00', '')
    
    enc = tiktoken.encoding_for_model(model)
    try:
        token_count = len(enc.encode(text))
        logger.debug(f"Token count: {token_count:,} for text of length {len(text):,}")
        return token_count
    except Exception as e:
        logger.error(f"Error counting tokens for text: {text[:100]}... Error: {str(e)}")
        return 0

def prepare_jsonl_files(chunks: List[Dict[str, Any]], base_output_path: str) -> List[str]:
    """
    Prepare JSONL files for batch embedding, splitting into files of max 20,000 records.
    Returns a list of created file paths.
    """
    output_paths = []
    total_chunks = len(chunks)
    
    logger.info(f"Preparing JSONL files with {total_chunks} total records (max {MAX_RECORDS_PER_BATCH_JSONL_FILE} per file)")
    
    # Calculate number of files needed
    num_files = (total_chunks + MAX_RECORDS_PER_BATCH_JSONL_FILE - 1) // MAX_RECORDS_PER_BATCH_JSONL_FILE
    
    for file_num in range(num_files):
        # Calculate chunk range for this file
        start_idx = file_num * MAX_RECORDS_PER_BATCH_JSONL_FILE
        end_idx = min((file_num + 1) * MAX_RECORDS_PER_BATCH_JSONL_FILE, total_chunks)
        
        # Generate file path
        if num_files == 1:
            current_path = base_output_path
        else:
            path_obj = Path(base_output_path)
            current_path = str(path_obj.parent / f"{path_obj.stem}_{file_num + 1}{path_obj.suffix}")
        
        # Write chunks to file with UTF-8 encoding
        logger.info(f"Creating file {file_num + 1}/{num_files}: {current_path}")
        record_count = 0
        token_count = 0
        
        with open(current_path, 'w', encoding='utf-8') as f:
            for chunk in chunks[start_idx:end_idx]:
                # Calculate tokens before writing to file
                chunk_tokens = get_token_count(chunk["content"], EMBEDDING_MODEL)
                token_count += chunk_tokens
                
                record = {
                    "custom_id": f"{chunk['recipe_id']}_{chunk['chunk_type']}",
                    "method": "POST",
                    "url": "/v1/embeddings",
                    "body": {
                        "model": EMBEDDING_MODEL,
                        "input": str(chunk["content"])
                    }
                }
                f.write(json.dumps(record, ensure_ascii=False) + '\n')
                record_count += 1
                
                # Log progress for large files
                if record_count % 1000 == 0:
                    logger.debug(f"Processed {record_count:,} records, {token_count:,} tokens so far...")
        
        logger.info(f"Created file with {record_count} records, and input tokens {token_count}: {current_path}")
        output_paths.append(current_path)
    
    logger.info(f"Created {len(output_paths)} JSONL files")
    return output_paths

def start_batch_job(file_path: str, model: str) -> str:
    """Create and start a batch embedding job using the latest OpenAI API."""
    client = openai.OpenAI(api_key=OPENAI_API_KEY)
    
    # Upload the JSONL file
    logger.info(f"Uploading JSONL file to OpenAI: {file_path}")
    file_response = client.files.create(
        file=open(file_path, "rb"),
        purpose="batch"
    )
    file_id = file_response.id
    logger.info(f"File uploaded successfully with ID: {file_id}")
    logger.info(f"File upload response: {file_response.to_json()}")
    
    # Create batch embedding job
    logger.info(f"Creating batch embedding job using model: {model}")
    descr = datetime.now().strftime("%Y%m%d_%H%M%S") + " - nutri chef embedding"
    batch_response = client.batches.create(
        input_file_id=file_id,
        endpoint="/v1/embeddings",
        completion_window="24h",
        metadata={
            "description": descr
        }
    )
    job_id = batch_response.id
    logger.info(f"Batch job created with ID: {job_id}")
    logger.info(f"Batch job response: {batch_response.to_json()}")
    return job_id

def save_job_id(job_id: str, input_file: str, temp_dir: Path):
    """Save job ID to a tracking file with timestamp."""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    jobs_file = temp_dir / "batch_jobs.txt"
    
    with open(jobs_file, 'a', encoding='utf-8') as f:
        f.write(f"{timestamp}\t{job_id}\t{input_file}\n")
    
    logger.info(f"Saved job ID {job_id} to tracking file: {jobs_file}")


def main():
    logger.info("Starting embedding generation process")
    
    # Create temp directory for JSONL files if it doesn't exist
    temp_dir = Path("batch-files")
    temp_dir.mkdir(exist_ok=True)
    logger.info(f"Created temporary directory at: {temp_dir}")
    
    try:
        # Connect to database and fetch recipes
        conn = get_db_connection()
        recipes = fetch_recipes(conn)
        
        # Chunk recipes
        logger.info("Starting recipe chunking process")
        all_chunks = []
        for recipe in recipes:
            all_chunks.extend(chunk_recipe(recipe))
        logger.info(f"Created total of {len(all_chunks)} chunks from {len(recipes)} recipes")
        
        # Prepare JSONL files
        base_jsonl_path = temp_dir / "recipes_for_embedding.jsonl"
        jsonl_paths = prepare_jsonl_files(all_chunks, str(base_jsonl_path))
        
        all_embeddings = []
        for file_num, jsonl_path in enumerate(jsonl_paths, 1):
            logger.info(f"Processing file {file_num}/{len(jsonl_paths)}: {jsonl_path}")
            
            # Create and monitor batch job for each file
            job_id = start_batch_job(jsonl_path, EMBEDDING_MODEL)
            logger.info(f"Batch job created with ID: {job_id}")
    
            # Save job ID for tracking
            save_job_id(job_id, jsonl_path, temp_dir)
        
            
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
     