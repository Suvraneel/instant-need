build:
	docker system prune -f
	docker compose build

up:
	docker system prune -f
	docker compose build
	docker compose up