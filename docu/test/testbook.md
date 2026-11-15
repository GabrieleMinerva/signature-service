# Testbook — Simple Signature Service

Avvia lo stack con `docker compose up --build`. Puoi eseguire le chiamate da [Swagger UI](http://localhost:8080/swagger-ui/index.html) o usare gli esempi nel [README](../../README.md). Le credenziali dimostrative sono `demo:demo`. Usa un nuovo documento per ogni prova di firma e conserva l’ID restituito. Esegui le prove di indisponibilità in un ambiente di test e riavvia ogni componente dopo la verifica.

## API e percorso di firma

| ID | Scenario e operazione | Risultato atteso |
| --- | --- | --- |
| API-01 | Chiama un endpoint senza credenziali o con password errata. | `401 Unauthorized`. |
| API-02 | Invia `POST /api/documents` con titolo `Accordo`. | `201 Created`, header `Location` e documento con UUID, titolo, data di creazione e stato `DRAFT`. |
| API-03 | Ripeti la creazione con lo stesso titolo. | `201 Created` con un UUID diverso. Entrambi i documenti sono consultabili. |
| API-04 | Invia un titolo assente, vuoto, composto solo da spazi o più lungo di 200 caratteri. | `400 Bad Request`. Nessun documento viene creato. |
| API-05 | Leggi il documento con `GET /api/documents/{id}`. | `200 OK` con i dati del documento e il suo stato. |
| API-06 | Leggi l’elenco con `GET /api/documents`, prima su un archivio vuoto e poi dopo una creazione. | `200 OK` con `[]` nel primo caso e un elenco contenente il nuovo documento nel secondo. |
| API-07 | Leggi o richiedi la firma con un UUID valido ma inesistente. Ripeti con un ID che non è un UUID. | `404 Not Found` per l’UUID inesistente e `400 Bad Request` per il formato errato. |
| FIR-01 | Con tutti i servizi attivi, invia `POST /api/documents/{id}/sign` su una bozza e rileggi il documento fino al completamento. | `202 Accepted` con header `Location`. Il passaggio in attesa può essere troppo breve per vederlo via GET. Entro il completamento della prova il documento risulta `SIGNED`. |
| FIR-02 | Invia due richieste di firma in parallelo sulla stessa bozza, con RabbitMQ disponibile. | Una richiesta riceve `202` e l’altra `409 Conflict`. Una sola richiesta viene pubblicata. |
| FIR-03 | Richiedi di nuovo la firma dopo che il documento è `SIGNED`. | `409 Conflict`. Il documento resta `SIGNED`. |
| FIR-04 | Reinvia su `signature.results` un esito già elaborato per lo stesso documento. | Il consumer conferma il messaggio e il documento resta `SIGNED`, senza una seconda transizione. |

## Indisponibilità e cache

| ID | Scenario e operazione | Risultato atteso |
| --- | --- | --- |
| OPS-01 | Ferma solo `signature-service`. Crea una bozza, chiedi la firma, ripeti la richiesta e poi riavvia il servizio. | La creazione restituisce `201`. La prima richiesta di firma restituisce `202` e resta in coda. Il documento è consultabile in `PENDING_SIGNATURE` e la seconda richiesta restituisce `409`. Dopo il riavvio il documento passa a `SIGNED`. |
| OPS-02 | Con il Document Service già avviato, ferma RabbitMQ prima di chiedere la firma di una bozza. | La pubblicazione fallisce e l’API restituisce `503 Service Unavailable`. La modifica già salvata può lasciare il documento in `PENDING_SIGNATURE`. Il riavvio del broker da solo non ripubblica la richiesta fallita. |
| OPS-03 | Leggi una bozza senza una chiave Redis associata. Controlla il valore e il TTL con `docker compose exec redis redis-cli GET document:status:{id}` e `docker compose exec redis redis-cli TTL document:status:{id}`, sostituendo `{id}` con l’UUID. | Il GET restituisce `200` e `DRAFT`. Redis contiene `DRAFT` e il TTL iniziale è di cinque minuti. Dopo la scadenza, il primo GET ripopola la chiave. |
| OPS-04 | Popola la cache leggendo una bozza, poi completa la firma e rileggi il documento. | Dopo l’elaborazione dell’esito e l’invalidazione, il GET restituisce `SIGNED` e ripopola la cache con quel valore. |
| OPS-05 | Ferma Redis con il Document Service già avviato e leggi un documento esistente. | Con PostgreSQL disponibile, il GET restituisce `200 OK` e lo stato letto dal database. |
| OPS-06 | Ferma PostgreSQL con il Document Service già avviato e prova a creare o consultare un documento, anche con lo stato già in cache. | L’operazione fallisce perché PostgreSQL serve anche per leggere i metadati. Gli errori di connessione o apertura della transazione gestiti dall’API producono `503`. |

## Verifiche automatizzate

- `./mvnw verify` su Linux/macOS o `.\mvnw.cmd verify` su Windows esegue i test REST con MockMvc e quelli su transizioni, cache e pubblicazione del risultato.
- Aggiungi `-Pcompose-it` per eseguire l’intero testbook con JUnit. `API-01` è in [DocumentApiTest](../../document-service/src/test/java/lab/signature/document/DocumentApiTest.java), gli altri casi sono in [DocumentTestbookIT](../../document-service/src/test/java/lab/signature/document/DocumentTestbookIT.java). Il profilo richiede Docker attivo e Compose 2.24.4 o successivo. Avvia uno stack isolato su porte assegnate automaticamente e rimuove i container, il volume e le immagini applicative temporanee al termine.
- `.\scripts\compose-smoke.ps1` prova il flusso con RabbitMQ, PostgreSQL e Redis reali, compresi worker fermo e riavvio, aggiornamento della cache, TTL ed esito ripetuto. Eseguilo con le porte locali 8080 e 15672 libere.
