# Meal Planner UI

Chainlit-based chat interface for the Meal Planner backend.

## Setup

**Create virtual environment**
```bash
python -m venv .venv
```

**Activate virtual environment**
```bash
# Linux / macOS / WSL
source .venv/bin/activate

# Windows (cmd)
.venv\Scripts\activate.bat

# Windows (PowerShell)
.venv\Scripts\Activate.ps1
```

**Install dependencies**
```bash
pip install -r requirements.txt
```

## Run

```bash
chainlit run app.py --port 8000
```

### Backend on Windows, UI in WSL

Use `host.docker.internal` to reach the Windows host from WSL:

```bash
SPRING_BASE_URL=http://host.docker.internal:8080 chainlit run app.py --port 8000
```

## Configuration

| Variable         | Default                  | Description              |
|------------------|--------------------------|--------------------------|
| `SPRING_BASE_URL` | `http://localhost:8080` | Meal Planner backend URL |
