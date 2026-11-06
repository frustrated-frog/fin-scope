FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    FINSCOPE_MARKET_DATA_DATA_DIR=/app/data

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
COPY src ./src
RUN uv sync --frozen --no-dev --extra ecosystem \
    && .venv/bin/python -c "import lightgbm"

# Small rolling fits otherwise start a large BLAS pool on every iteration.
# LightGBM also declares its own two-thread limit in the model recipe.
ENV OPENBLAS_NUM_THREADS=1 \
    OMP_NUM_THREADS=2 \
    MKL_NUM_THREADS=1

EXPOSE 8000

CMD ["uv", "run", "uvicorn", "finscope_market_data.app:app", "--host", "0.0.0.0", "--port", "8000"]
