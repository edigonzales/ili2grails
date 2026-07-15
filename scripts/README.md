````bash
docker compose up -d edit-db
./scripts/create-grails-app.sh styling-lab
```

```bash
cd generated-grails/styling-lab
DB_USERNAME=postgres DB_PASSWORD=secret ./gradlew bootRun
```