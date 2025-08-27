from openai import OpenAI
from dotenv import load_dotenv
import os
from pathlib import Path
from datetime import datetime

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=OPENAI_API_KEY)

# batches = client.batches.list(limit=10)
# print(f"batches:\n {batches}")

# batch1 = ""
batch2 = ""

# batch = client.batches.retrieve(batch1)
# print(f"batch1:\n {batch.to_json()}")

batch = client.batches.retrieve(batch2)
print(f"batch2:\n {batch.to_json()}")



file_name = ""

file_response = client.files.content(file_name)

# Create output directory if it doesn't exist
output_dir = Path("batch-files")

# Generate filename with timestamp
timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
output_file = output_dir / f"{file_name}.jsonl"

# Save the response to file
with open(output_file, "w", encoding='utf-8') as f:
    f.write(file_response.text)

print(f"Response saved to: {output_file}")
print(f"First few lines of the response:")
print(file_response.text[:500] + "...")