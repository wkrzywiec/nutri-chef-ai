import os
import json

import chainlit as cl
import httpx


SPRING_BASE_URL = os.environ.get("SPRING_BASE_URL", "http://localhost:8080")
SPRING_API_KEY = os.environ.get("SPRING_API_KEY", "")


def _auth_headers() -> dict:
    return {"X-Api-Key": SPRING_API_KEY} if SPRING_API_KEY else {}


async def _consume_sse(prompt: str):
    """Yield parsed event dicts from the SSE endpoint."""
    url = f"{SPRING_BASE_URL}/api/planner/single"
    headers = {**_auth_headers(), "Accept": "text/event-stream"}
    async with httpx.AsyncClient(timeout=None) as client:
        async with client.stream("GET", url, params={"prompt": prompt}, headers=headers) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                line = line.strip()
                if not line.startswith("data:"):
                    continue
                data = line[len("data:"):].strip()
                if not data:
                    continue
                try:
                    yield json.loads(data)
                except json.JSONDecodeError:
                    pass


@cl.on_chat_start
async def on_chat_start():
    await cl.Message(
        content=(
            "**Meal Planner**\n\n"
            "Ask me for meal ideas! For example:\n"
            "- *high protein dinner under 30 minutes*\n"
            "- *vegetarian lunch ideas*\n"
            "- *quick breakfast with oats*"
        )
    ).send()


@cl.action_callback("follow_up")
async def on_follow_up_action(action: cl.Action):
    await on_message(cl.Message(content=action.payload["prompt"]))


@cl.on_message
async def on_message(message: cl.Message):
    prompt = message.content.strip()
    if not prompt:
        await cl.Message(content="Please send a prompt.").send()
        return

    msg = cl.Message(content="")
    await msg.send()

    recipes: list[dict] = []
    follow_ups: list[str] = []

    try:
        async for event in _consume_sse(prompt):
            event_type = event.get("type")
            payload = event.get("payload")

            if event_type == "status":
                phase = (payload or {}).get("phase", "")
                status_msg = (payload or {}).get("message", "")
                # Only surface search-phase status – LLM call-started events
                # are implicitly communicated through the token stream itself.
                if phase == "search":
                    await msg.stream_token(f"\n*{status_msg}*\n\n")

            elif event_type == "response.token":
                await msg.stream_token(str(payload))

            elif event_type == "recipe.selected":
                recipes.append(payload or {})

            elif event_type == "suggested.follow.ups":
                follow_ups = list(payload or [])

            elif event_type == "final":
                await msg.update()

                recipe_elements = (
                    [cl.CustomElement(name="RecipeGrid", props={"recipes": recipes}, display="inline")]
                    if recipes else []
                )
                follow_up_actions = [
                    cl.Action(name="follow_up", label=f, payload={"prompt": f})
                    for f in follow_ups
                ]

                if recipe_elements or follow_up_actions:
                    content_parts = []
                    if recipes:
                        content_parts.append("**Suggested recipes:**")
                    await cl.Message(
                        content="\n".join(content_parts),
                        elements=recipe_elements,
                        actions=follow_up_actions,
                    ).send()

            elif event_type == "error":
                reason = (payload or {}).get("message", "Unknown error")
                await msg.stream_token(f"\n\n❌ **Error:** {reason}")

        await msg.update()

    except Exception as e:
        await msg.stream_token(f"\n\n❌ **Failed to reach the backend:** {e}")
        await msg.update()
