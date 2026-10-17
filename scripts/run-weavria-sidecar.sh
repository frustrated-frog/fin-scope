#!/usr/bin/env bash

set -euo pipefail

finscope_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_weavria_root="$(cd "${finscope_root}/../.." && pwd)/weavria"
weavria_root="${WEAVRIA_PROJECT_DIR:-${default_weavria_root}}"
application_config="${finscope_root}/backend/finscope-web/src/main/resources/application.yml"
local_properties="${FINSCOPE_LLM_LOCAL_PROPERTIES:-${HOME}/.config/finscope/llm.local.properties}"

read_llm_setting() {
  local setting="$1"
  awk -v setting="${setting}" '
    /^  llm:[[:space:]]*$/ { inside = 1; next }
    inside && /^  [^[:space:]][^:]*:/ { exit }
    inside && $0 ~ "^    " setting ":[[:space:]]*" {
      sub("^    " setting ":[[:space:]]*", "")
      gsub(/^['\"']|['\"']$/, "")
      print
      exit
    }
  ' "${application_config}"
}

read_local_property() {
  local property="$1"
  awk -v property="${property}" '
    /^[[:space:]]*#/ { next }
    index($0, property "=") == 1 {
      sub("^[^=]*=", "")
      print
      exit
    }
  ' "${local_properties}"
}

if [[ ! -d "${weavria_root}" || ! -f "${weavria_root}/pyproject.toml" ]]; then
  echo "Weavria project not found: ${weavria_root}" >&2
  echo "Set WEAVRIA_PROJECT_DIR to its checkout directory." >&2
  exit 1
fi

if [[ ! -f "${application_config}" ]]; then
  echo "FinScope application config not found: ${application_config}" >&2
  exit 1
fi

if [[ ! -f "${local_properties}" && -z "${WEAVRIA_MODEL_API_KEY:-}" ]]; then
  echo "FinScope local LLM properties not found: ${local_properties}" >&2
  exit 1
fi

export WEAVRIA_MODEL_BASE_URL="${WEAVRIA_MODEL_BASE_URL:-$(read_llm_setting base-url)}"
export WEAVRIA_MODEL_NAME="${WEAVRIA_MODEL_NAME:-$(read_llm_setting model)}"
export WEAVRIA_MODEL_API_KEY="${WEAVRIA_MODEL_API_KEY:-$(read_local_property finscope.llm.api-key)}"
export WEAVRIA_MODEL_JSON_MODE="${WEAVRIA_MODEL_JSON_MODE:-true}"
export WEAVRIA_FINSCOPE_BASE_URL="${WEAVRIA_FINSCOPE_BASE_URL:-http://127.0.0.1:8080}"
export WEAVRIA_ALLOWED_PARENT_ORIGIN="${WEAVRIA_ALLOWED_PARENT_ORIGIN:-http://localhost:5173}"
export WEAVRIA_STATE_PATH="${WEAVRIA_STATE_PATH:-${weavria_root}/.weavria/finscope-sessions.sqlite3}"
weavria_port="${WEAVRIA_PORT:-8766}"

if [[ -z "${WEAVRIA_MODEL_BASE_URL}" || -z "${WEAVRIA_MODEL_NAME}" \
  || -z "${WEAVRIA_MODEL_API_KEY}" ]]; then
  echo "FinScope LLM base URL, model or local API key is missing." >&2
  exit 1
fi

cd "${weavria_root}"
exec uv run --extra finscope uvicorn apps.finscope.server:create_app \
  --factory --host 127.0.0.1 --port "${weavria_port}"
