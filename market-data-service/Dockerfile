FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    FINSCOPE_MARKET_DATA_DATA_DIR=/app/data

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
COPY src ./src
RUN uv sync --frozen --no-dev --extra ecosystem

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "finscope_market_data.app:app", "--host", "0.0.0.0", "--port", "8000"]

