FROM rust:1.83-bookworm AS chef
RUN cargo install cargo-chef --locked --version 0.1.71
WORKDIR /app

FROM chef AS planner
COPY . .
RUN cargo chef prepare --recipe-path recipe.json

FROM chef AS builder
COPY --from=planner /app/recipe.json recipe.json
RUN cargo chef cook --release --recipe-path recipe.json
COPY . .
RUN cargo build --release --bin embedding-sidecar

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates libssl3 libgomp1 curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd -u 10001 -m sidecar
COPY --from=builder /app/target/release/embedding-sidecar /usr/local/bin/embedding-sidecar
USER sidecar
VOLUME ["/app/models"]
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS http://localhost:8000/healthz || exit 1
ENTRYPOINT ["embedding-sidecar"]
