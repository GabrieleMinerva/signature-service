# Analisi funzionale

Il servizio permette di registrare documenti e seguirne la firma simulata. Tutte le operazioni rivolte all’utente richiedono credenziali valide. In caso contrario il servizio risponde con `401`.

## 1. Registrare un documento

L’utente invia un titolo non vuoto di massimo 200 caratteri. Il servizio registra la bozza e restituisce identificativo, titolo, stato e data di creazione.

```mermaid
flowchart TD
    A["L'utente invia il titolo"] --> B{"Titolo valido?"}
    B -->|Sì| C["Registra la bozza e restituisce il documento"]
    B -->|No| D["Rifiuta la richiesta con 400"]
```

## 2. Consultare un documento

L’utente usa l’identificativo ricevuto alla creazione per leggere il documento e controllare lo stato della firma.

```mermaid
flowchart TD
    A["L'utente indica l'identificativo"] --> B{"Il documento esiste?"}
    B -->|Sì| C["Mostra i dati e lo stato"]
    B -->|No| D["Risponde con 404"]
```

## 3. Elencare i documenti

L’utente può consultare tutti i documenti registrati con il relativo stato. Se l’archivio è vuoto, riceve un elenco vuoto.

```mermaid
flowchart TD
    A["L'utente chiede l'elenco"] --> B{"Ci sono documenti?"}
    B -->|Sì| C["Mostra i documenti con i rispettivi stati"]
    B -->|No| D["Restituisce un elenco vuoto"]
```

## 4. Richiedere la firma

Una bozza passa in attesa di firma. L’utente riceve conferma della presa in carico e può consultare il documento in seguito. Una nuova richiesta per un documento già in attesa o firmato restituisce `409`.

```mermaid
flowchart TD
    A["L'utente chiede la firma"] --> B{"Il documento esiste?"}
    B -->|No| C["Risponde con 404"]
    B -->|Sì| D{"Il documento è in bozza?"}
    D -->|No| E["Rifiuta la nuova richiesta con 409"]
    D -->|Sì| F["Imposta lo stato in attesa"]
    F --> G{"Richiesta accodata?"}
    G -->|Sì| H["Conferma la presa in carico con 202"]
    G -->|Errore di pubblicazione| I["Risponde con 503. Il documento può restare in attesa"]
```

## 5. Completare la firma

Il servizio di firma elabora la richiesta in background. Se è fermo e RabbitMQ è disponibile, la richiesta resta in coda e viene elaborata dopo il riavvio. Al primo esito valido, il documento in attesa diventa firmato. Gli esiti ripetuti lasciano invariato lo stato.

```mermaid
flowchart TD
    A["Richiesta accodata"] --> B{"Servizio di firma disponibile?"}
    B -->|No| C["La richiesta attende in coda"]
    C -->|Al riavvio| D["Simula la firma e invia l'esito"]
    B -->|Sì| D
    D --> E{"Il documento è ancora in attesa?"}
    E -->|Sì| F["Lo stato diventa firmato ed è consultabile"]
    E -->|No| G["Lo stato resta invariato"]
```

## Risposte delle API

| Metodo e percorso | Risposta |
| --- | --- |
| `POST /api/documents` | `201` con documento e header `Location` |
| `GET /api/documents/{id}` | `200` con documento |
| `GET /api/documents` | `200` con elenco |
| `POST /api/documents/{id}/sign` | `202` con header `Location` |

Un titolo non valido o un identificativo con formato errato restituisce `400`.
