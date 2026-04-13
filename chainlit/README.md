# Meal Planner UI

Chainlit-based chat interface for the Meal Planner backend.

## Setup

### With uv (recommended)

Install dependencies and create the virtual environment in one step:

```bash
uv sync
```

## Run

### With uv

```bash
uv run chainlit run app.py --port 8000
```

### Backend on Windows, UI in WSL

Use `host.docker.internal` to reach the Windows host from WSL:

```bash
SPRING_BASE_URL=http://host.docker.internal:8080 uv run chainlit run app.py --port 8000
```

## Configuration

| Variable          | Default                  | Description              |
|-------------------|--------------------------|--------------------------|
| `SPRING_BASE_URL` | `http://localhost:8080`  | Meal Planner backend URL |

