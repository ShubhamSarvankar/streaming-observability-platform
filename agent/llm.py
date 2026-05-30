import json
import os


class LLM:
    def complete(self, system: str, user: str) -> str:
        raise NotImplementedError

    def extract(self, system: str, user: str, schema: dict) -> dict:
        raise NotImplementedError


class AnthropicLLM(LLM):
    def __init__(self):
        import anthropic
        self._client = anthropic.Anthropic(api_key=os.environ["LLM_API_KEY"])
        self._model = os.environ.get("LLM_MODEL", "claude-haiku-4-5")

    def complete(self, system, user):
        msg = self._client.messages.create(
            model=self._model, max_tokens=1024,
            system=system, messages=[{"role": "user", "content": user}])
        return "".join(b.text for b in msg.content if b.type == "text")

    def extract(self, system, user, schema):
        instruction = (
            system + "\n\nReturn ONLY a JSON object matching this schema, "
            "no prose, no markdown fences:\n" + json.dumps(schema))
        raw = self.complete(instruction, user).strip()
        if raw.startswith("```"):
            raw = raw.strip("`")
            raw = raw[raw.find("{"):raw.rfind("}") + 1]
        return json.loads(raw)


def _build_json_schema(schema: dict) -> dict:
    props = {}
    for key, desc in schema.items():
        desc_str = str(desc).lower()
        if "integer" in desc_str:
            t = ["integer", "null"] if "null" in desc_str else "integer"
        elif "null" in desc_str:
            t = ["string", "null"]
        else:
            t = "string"
        props[key] = {"type": t, "description": str(desc)}
    return {"type": "object", "properties": props}


class OllamaLLM(LLM):
    def __init__(self):
        import ollama
        host = os.environ.get("OLLAMA_HOST", "http://host.docker.internal:11434")
        self._client = ollama.Client(host=host)
        self._model = os.environ.get("LLM_MODEL", "qwen2.5:7b")

    def complete(self, system, user):
        resp = self._client.chat(
            model=self._model,
            messages=[
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        )
        return resp.message.content

    def extract(self, system, user, schema):
        json_schema = _build_json_schema(schema)
        instruction = (
            system + "\n\nReturn a JSON object matching this schema:\n"
            + json.dumps(schema))
        resp = self._client.chat(
            model=self._model,
            messages=[
                {"role": "system", "content": instruction},
                {"role": "user", "content": user},
            ],
            format=json_schema,
        )
        return json.loads(resp.message.content)


def get_llm() -> LLM:
    provider = os.environ.get("LLM_PROVIDER", "anthropic")
    if provider == "anthropic":
        return AnthropicLLM()
    if provider == "ollama":
        return OllamaLLM()
    raise ValueError(f"unsupported LLM_PROVIDER: {provider}")
