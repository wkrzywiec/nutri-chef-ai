from openai import OpenAI
from dotenv import load_dotenv
import os
from pathlib import Path
from datetime import datetime

load_dotenv()

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=OPENAI_API_KEY)

file_name = ""

file_response = client.files.content(file_name)

output_dir = Path("batch-files")

output_file = output_dir / f"{file_name}.jsonl"

with open(output_file, "w", encoding='utf-8') as f:
    f.write(file_response.text)

print(f"Response saved to: {output_file}")