# Simple Signature Service

Servizio dimostrativo per registrare documenti, richiedere una firma simulata e consultarne lo stato. Per approfondire, consulta [requisiti di business](docu/brb.md), [analisi funzionale](docu/analisi-funzionale.md) e [analisi tecnica](docu/analisi-tecnica.md).

## Avvio

```sh
docker compose up --build
```

API su `http://localhost:8080` e RabbitMQ Management su `http://localhost:15672`, entrambe con credenziali dimostrative `demo:demo`. Configurare HTTP Basic con `DOCUMENT_API_USER` e `DOCUMENT_API_PASSWORD`.

Apri [Swagger UI](http://localhost:8080/swagger-ui/index.html) per consultare e provare le API con le credenziali HTTP Basic del servizio. Se lo stack era già avviato, ricostruisci il Document Service con `docker compose up -d --build document-service`.

### Provare l’API da Windows PowerShell

```powershell
$base = 'http://localhost:8080/api/documents'
$token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('demo:demo'))
$headers = @{ Authorization = "Basic $token" }
$body = @{ title = 'Accordo' } | ConvertTo-Json

$document = Invoke-RestMethod -Uri $base -Method Post -Headers $headers -ContentType 'application/json' -Body $body
$document
$url = "$base/$($document.id)"
Invoke-RestMethod -Uri $base -Headers $headers
Invoke-WebRequest -Uri "$url/sign" -Method Post -Headers $headers -UseBasicParsing
Invoke-RestMethod -Uri $url -Headers $headers
```

### Provare l’API da Bash

```sh
curl -i -u demo:demo -H 'Content-Type: application/json' -d '{"title":"Accordo"}' http://localhost:8080/api/documents
curl -i -u demo:demo http://localhost:8080/api/documents
curl -i -u demo:demo -X POST http://localhost:8080/api/documents/$ID/sign
curl -i -u demo:demo http://localhost:8080/api/documents/$ID
```

Impostare `ID` con l’UUID restituito dalla creazione. Ripetere il GET per osservare `SIGNED`.

## Test

Con Java 21 eseguire `./mvnw verify` su Linux e macOS o `.\mvnw.cmd verify` su Windows. La suite copre autenticazione, REST, transizioni, cache e pubblicazione del risultato.

Lo smoke test PowerShell `.\scripts\compose-smoke.ps1` usa i servizi reali. Richiede Docker attivo e le porte locali 8080 e 15672 libere. Avvia un progetto Compose temporaneo e rimuove i relativi container e volumi al termine.

## Arresto

```sh
docker compose down
```
