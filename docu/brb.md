# BRB — Requisiti di business

- Il servizio deve permettere di registrare un documento e seguirne la firma simulata.
- Solo gli utenti autenticati possono accedere al servizio.
- Ogni documento deve avere un titolo e una data di creazione.
- Ogni documento deve essere identificabile in modo univoco, anche se più documenti hanno lo stesso titolo.
- L’utente deve poter consultare un singolo documento o vedere l’elenco dei documenti registrati.
- È possibile richiedere la firma solo quando il documento è in bozza.
- Il servizio deve confermare la presa in carico della richiesta e permettere di consultarne l’esito in seguito.
- Lo stato deve indicare chiaramente se il documento è in bozza, in attesa di firma o firmato.
- Una seconda richiesta sullo stesso documento già in lavorazione o firmato deve essere rifiutata.
- Se il completamento della firma viene comunicato più volte, il documento deve risultare firmato una sola volta.
