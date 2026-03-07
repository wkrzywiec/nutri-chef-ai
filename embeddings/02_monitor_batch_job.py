from openai import OpenAI
from dotenv import load_dotenv
import os

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=OPENAI_API_KEY)

batch_id = ""

batch = client.batches.retrieve(batch_id)
print(f"Batch response:\n {batch.to_json()}")