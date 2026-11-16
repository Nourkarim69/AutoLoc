# Notes de monitoring — Prometheus et Grafana

## Architecture

```
Spring Boot (IntelliJ, port 8081)
  └─ Actuator + Micrometer  →  GET /actuator/prometheus  (métriques au format Prometheus)
        ↑ toutes les 5 s
Prometheus (Docker, port 9090)   collecte et stocke les séries temporelles
        ↑ requêtes PromQL
Grafana (Docker, port 3000)      affiche les tableaux de bord
```

| Composant | Rôle |
|---|---|
| Spring Boot Actuator | Expose la santé (`/actuator/health`) et les métriques de l'application. |
| Micrometer Prometheus Registry | Met les métriques au format lu par Prometheus (`/actuator/prometheus`). |
| Prometheus | Interroge l'application toutes les 5 s et stocke les valeurs dans le temps. |
| Grafana | Interroge Prometheus et dessine les graphiques. |

Fichiers : `pom.xml` (actuator + micrometer-registry-prometheus), `application.properties` (section Monitoring),
`monitoring/prometheus.yml`, `docker-compose.yml`, `monitoring/grafana/…` (source de données et tableau de bord
chargés automatiquement), `monitoring/generate-traffic.ps1` (envoi de trafic).

## Lancer

1. Démarrer MySQL (XAMPP), puis l'application dans IntelliJ (port 8081).
2. Démarrer Docker Desktop, puis dans le dossier `autoloc-api` :
   ```
   docker compose up -d
   docker compose ps
   ```
3. Prometheus : http://localhost:9090 → *Status → Targets* : `autoloc-api` doit être **UP**.
4. Grafana : http://localhost:3000 (`admin` / `admin` au premier lancement, puis changer le mot de passe)
   → *Dashboards → AutoLoc → AutoLoc — Application Monitoring*.
5. Générer du trafic : `.\monitoring\generate-traffic.ps1` (ou des requêtes Postman), régler *Last 5 minutes*.
6. Arrêter : `docker compose down`, puis arrêter Spring Boot dans IntelliJ.

La source de données Prometheus (`http://prometheus:9090`, nom du service Docker et non `localhost`) et le
tableau de bord sont **provisionnés** : ils existent dès le démarrage de Grafana, sans clic. On peut aussi les
créer à la main comme dans le TP (*Connections → Data sources → Prometheus*).

## Métriques choisies (tableau de bord « AutoLoc — Application Monitoring »)

| Panneau | Requête PromQL | Pourquoi |
|---|---|---|
| Application UP | `up{job="autoloc-api"}` | Voir tout de suite si l'application répond à Prometheus. |
| Uptime | `process_uptime_seconds` | Détecter un redémarrage inattendu. |
| Live threads | `jvm_threads_live_threads` | Une hausse continue signale des requêtes bloquées. |
| DB connections in use | `sum(hikaricp_connections_active)` | Connexions MySQL du pool HikariCP utilisées (pool saturé = lenteurs). |
| HTTP errors 5xx | `sum(increase(http_server_requests_seconds_count{status=~"5.."}[5m]))` | Erreurs serveur des 5 dernières minutes. |
| HTTP request rate | `sum by (uri) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))` | Requêtes par seconde, par route (sans les appels de Prometheus). |
| HTTP response time p95 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))` | 95 % des requêtes répondent plus vite que cette valeur. |
| JVM memory used | `sum by (area) (jvm_memory_used_bytes)` | Mémoire *heap* / *non-heap* de la JVM. |
| CPU usage | `process_cpu_usage` | Charge processeur du processus Spring Boot. |

Pour le p95, `management.metrics.distribution.percentiles-histogram.http.server.requests=true` publie les
*buckets* d'histogramme nécessaires à `histogram_quantile`.

## Problème rencontré et résolu

**La cible `autoloc-api` était DOWN dans Prometheus** (*Status → Targets*).

1. Le TP utilise le port **8080**, mais AutoLoc tourne sur **8081** (8080 est déjà pris sur ce PC) :
   la cible pointait vers un port sans application. → `monitoring/prometheus.yml` vise `host.docker.internal:8081`.
2. Ensuite la cible répondait **HTTP 404** sur `/actuator/prometheus` : l'application lancée dans IntelliJ
   avait été démarrée **avant** l'ajout des dépendances dans le `pom.xml`, donc sans Actuator.
   → *Maven → Reload All Maven Projects*, puis redémarrer l'application : la cible passe **UP**.

À vérifier dans le même cas : `http://localhost:8081/actuator/prometheus` doit afficher des lignes de métriques,
et Spring Security doit laisser passer `/actuator/prometheus` (règle `permitAll` déclarée avant les autres).

## Observation

Sur le panneau *HTTP response time p95*, les routes protégées par HTTP Basic (`/api/hello`, `/api/admin/report`)
montent à environ **0,4 à 0,5 s** : chaque requête Basic refait la vérification BCrypt (coût 12). C'est le prix
voulu de BCrypt ; le site, lui, vérifie le mot de passe une seule fois à la connexion puis utilise la session.
