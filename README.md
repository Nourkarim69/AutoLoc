# AutoLoc — ECE UP ASI (Architecture des Systèmes d'Information)

Projet d'agence de location de véhicules « AutoLoc », construit au fil des workshops ASI :
une API REST Spring Boot + MySQL et un site web (servi par la même application).

| Dossier | Contenu |
|---|---|
| `autoloc-api/` | Application Spring Boot + Maven + Spring Data JPA + Lombok (API REST + site web). |
| `autoloc-api/docs/` | `fetch-strategy.md`, `security-notes.md`, `monitoring-notes.md` (livrables de la Session 3). |
| `autoloc-api/monitoring/` + `docker-compose.yml` | Prometheus + Grafana (Docker) pour surveiller l'application. |
| `postman/` | Collection Postman de toutes les routes de l'API. |
| `rapport/` | Rapport PDF, fiche de révision et partie performance. |
| `uml/location.mdj` | Diagramme de classes StarUML : `Client` / `Vehicule`, association `louer` (0..1 / 0..*). |
| `java-simple/` | Version Java simple de ce diagramme (sans framework), package `modele` + `Main`. |

## Avancement

| Étape | Contenu |
|---|---|
| **Workshop 1** | Projet Spring Boot, connexion MySQL, entité `Vehicule`, puis les 8 autres entités et 5 enums (sans associations). Bonus : `CommandLineRunner` + profil `dev`. |
| **Workshop 2** | Associations, cascade et fetch selon le tableau du TP : toutes LAZY + `@EntityGraph` (voir plus bas et `docs/fetch-strategy.md`). |
| **API REST** | Contrôleurs `web.controller`, DTO validés `web.dto`, logique métier `service`, erreurs JSON en français. |
| **Sécurité** | **Spring Security** (`SecurityFilterChain`, HTTP Basic, utilisateurs en mémoire), mots de passe BCrypt, session, rôles, anti force brute, profil `prod`. |
| **Monitoring** | Actuator + Micrometer, Prometheus et Grafana dans Docker, tableau de bord de 9 panneaux. |
| **Site web** | Accueil, catalogue, réservation, agences, connexion, espace client, administration. |
| **Tests** | 40 tests JUnit 5 (règles de réservation, droits d'accès, anti force brute) + benchmark de performance. |

## Prérequis
- JDK 17+
- IntelliJ IDEA (plugin Lombok + *Enable annotation processing*)
- MySQL (ou MariaDB) démarré sur `localhost:3306` (par exemple XAMPP → *Start* sur la ligne MySQL)
- Postman (facultatif)
- Docker Desktop (pour le monitoring uniquement)

## Lancer le projet
```bash
git clone https://github.com/Nourkarim69/AutoLoc.git
```
1. IntelliJ → *File → Open…* → choisir le dossier **`autoloc-api`** (pas la racine du dépôt). Maven télécharge les dépendances.
2. Démarrer MySQL. La base `autoloc_db` est créée automatiquement (`createDatabaseIfNotExist=true`).
3. Lancer `AutolocApiApplication` (bouton ▶ vert).
4. Dans la console : `Started AutolocApiApplication`. Hibernate crée les tables ; le profil `dev` insère les données de démo.
5. Ouvrir **http://localhost:8081**.

En ligne de commande, dans `autoloc-api/` : `mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run`.
Tests : `mvnw.cmd test` (MySQL doit être démarré pour le test de démarrage de l'application).

### Comptes
| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Démo Spring Security (HTTP Basic, en mémoire) | `student` / `admin` | `student123` / `admin123` (variables `DEMO_USER_PASSWORD` / `DEMO_ADMIN_PASSWORD`) |
| Administrateur (créé au démarrage en `dev`) | `admin@autoloc.fr` | `Admin2026!` (variables `ADMIN_EMAIL` / `ADMIN_PASSWORD`) |
| Client | à créer sur la page **Connexion → Je m'inscris** | 8 caractères min., lettres et chiffres |

### Données de démo (profil `dev`)
3 agences (Paris, Lyon, Marseille), 3 véhicules rattachés à une agence, 4 équipements (GPS, siège bébé, chaînes neige, conducteur supplémentaire) et le compte administrateur.
Ces données ne sont insérées que dans des tables vides.

## Le site web
| Page | Ce qu'on y fait |
|---|---|
| Accueil | Réservation rapide (liste déroulante + dates), chiffres clés, véhicules disponibles. |
| Véhicules | Catalogue avec recherche, filtres (catégorie, statut) et tri par prix. |
| Réserver | Liste déroulante des voitures, **date de prise et date de retour**, options, récapitulatif et prix en direct ; les dates déjà réservées sont signalées. |
| Agences | Coordonnées et lien vers la carte. |
| Connexion | Connexion / inscription (indicateur de force du mot de passe). |
| **Mon espace** (client) | Mes réservations (**prolonger**, annuler), mes contrats et paiements, changer mon mot de passe. |
| **Administration** | Statistiques ; réservations (confirmer → contrat créé, terminer, annuler, prolonger) ; contrats et paiements ; véhicules ; maintenances ; agences ; équipements ; employés ; clients (réinitialiser le mot de passe). |

### Règles de réservation
- La date de prise ne peut pas être dans le passé, le retour doit être après la prise, 90 jours maximum.
- Pas de double réservation : deux réservations actives d'une même voiture ne peuvent pas se chevaucher (le jour du retour peut être le jour de prise suivant).
- Une voiture en maintenance ne peut pas être réservée.
- Prix = nombre de jours × (tarif de la voiture + tarif des options).
- Prolongation : nouvelle date de retour plus tardive, voiture libre sur la période ajoutée ; le contrat est mis à jour.
- Un client peut annuler avant le jour de prise ; ensuite, seule l'agence peut le faire.
- Confirmer une réservation crée son contrat ; l'annuler invalide le contrat. Un paiement ne peut pas dépasser le reste à payer.

## Modèle (Workshop 2) : associations, cascade et fetch

Toutes les associations sont **LAZY** (recommandation du TP) ; les listes de l'API chargent ce dont elles ont besoin
en une requête avec `@EntityGraph`. Justification complète : [`autoloc-api/docs/fetch-strategy.md`](autoloc-api/docs/fetch-strategy.md).

| Association | Mapping / côté propriétaire | Fetch | Cascade |
|---|---|---|---|
| Contrat → Paiements | `@OneToMany(mappedBy)` / `@ManyToOne`, Paiement possède `id_contrat` | LAZY | ALL + `orphanRemoval` |
| Agence → Véhicules | `@OneToMany(mappedBy)` / `@ManyToOne`, Véhicule possède `id_agence` | LAZY | aucune |
| Agence → Employés | `@OneToMany(mappedBy)` / `@ManyToOne`, Employé possède `id_agence` | LAZY | aucune |
| Véhicule ↔ Équipement | `@ManyToMany`, table `vehicule_equipement` (Véhicule propriétaire), `Set` | LAZY | aucune |
| Client → Réservations | `@OneToMany(mappedBy)` / `@ManyToOne`, Réservation possède `id_client` | LAZY | PERSIST |
| Réservation → Véhicule | `@ManyToOne`, Réservation possède `id_vehicule` | LAZY | aucune |
| Réservation ↔ Contrat | `@OneToOne`, Contrat possède `id_reservation` (unique) | LAZY | ALL côté Réservation |
| Véhicule → Maintenances | `@OneToMany(mappedBy)` / `@ManyToOne`, Maintenance possède `id_vehicule` | LAZY | PERSIST |
| Réservation ↔ Équipement (options) | `@ManyToMany`, table `reservation_equipement` | EAGER + `@BatchSize(50)` | aucune |

Les collections `@OneToMany` sont annotées `@JsonIgnore` pour éviter les boucles infinies en JSON. Lombok reste ciblé (`@Getter` / `@Setter`, pas de `@Data`).

## Architecture du code (`fr.ece.autoloc`)
```
domain           entités JPA + enums
repository       interfaces Spring Data JPA (requêtes, @EntityGraph)
service          règles métier (ReservationService, ContratService, MaintenanceService)
security         BCrypt, session, contrôle d'accès (AuthInterceptor), anti force brute
web.controller   contrôleurs REST + ApiExceptionHandler
web.dto          objets reçus par l'API, validés avec Bean Validation
```

## API REST
Collection complète : `postman/AutoLoc.postman_collection.json` (Postman garde le cookie de session après la connexion).

| Accès | Routes |
|---|---|
| Public | `GET /api/public/ping`, `GET /actuator/health`, `GET /actuator/prometheus`, `GET /api/vehicules/{id}/equipements`, `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/vehicules[/{id}]`, `GET /api/agences[/{id}]`, `GET /api/equipements`, `GET /api/reservations/indisponibilites?vehicule={id}` |
| Connecté (session du site ou HTTP Basic USER/ADMIN) | `GET /api/hello`, `PATCH /api/auth/password`, `POST /api/reservations`, `GET /api/reservations/mine`, `PATCH /api/reservations/{id}/prolonger`, `PATCH /api/reservations/{id}/annuler`, `GET /api/contrats/mine`, `GET /api/contrats/{id}/paiements` |
| Admin | `GET /api/admin/report`, `PUT /api/vehicules/{id}/equipements`, `GET /api/admin/stats`, `GET /api/reservations?page=&size=`, `PATCH /api/reservations/{id}/statut`, `GET /api/contrats`, `POST /api/contrats/{id}/paiements`, `PATCH /api/contrats/{id}/invalider`, CRUD `/api/vehicules`, `/api/agences`, `/api/equipements`, `/api/employes`, `/api/maintenances` (+ `PATCH /{id}/terminer`), `GET /api/clients?page=&size=`, `DELETE /api/clients/{id}`, `POST /api/clients/{id}/reinitialiser-mot-de-passe` |

Les erreurs sont renvoyées en JSON : `{"status": 400, "message": "...", "errors": {"champ": "..."}}`
(400 donnée invalide, 401 non connecté, 403 interdit, 404 introuvable, 409 conflit, 429 trop de tentatives).

## Sécurité
- **Spring Security** : `SecurityFilterChain` (règles de la plus précise à la plus large), HTTP Basic avec deux comptes en mémoire
  (`student` = USER, `admin` = ADMIN), réponses JSON 401 / 403. La session du site est reliée à Spring Security
  (client = USER, employé = ADMIN). Détails et réponses aux questions du TP : [`autoloc-api/docs/security-notes.md`](autoloc-api/docs/security-notes.md).
- Mots de passe hachés avec **BCrypt** (coût 12, sel aléatoire) ; jamais stockés ni renvoyés en clair.
- Session HTTP : cookie `HttpOnly` + `SameSite=Strict`, nouvel identifiant à chaque connexion, expiration 1 h.
- Droits vérifiés côté serveur pour chaque route ; un client ne voit que ses propres réservations et contrats.
- **Anti force brute** : 5 échecs pour un même email et une même IP bloquent la connexion 15 minutes (HTTP 429).
- Même message d'erreur que l'email existe ou non.
- Mot de passe oublié : l'agence génère un mot de passe temporaire (Administration → Clients), que le client change ensuite dans « Mon compte ».
- Aucune trace Java dans les réponses d'erreur.
- Profil **`prod`** (`SPRING_PROFILES_ACTIVE=prod`) : `ddl-auto=validate`, pas de données de démo ni de logs SQL, cookie `Secure`, HTTPS activable (`SSL_ENABLED`, `SSL_KEYSTORE`, `SSL_KEYSTORE_PASSWORD`, voir `application-prod.properties`).

## Performances
- Pas de requêtes « N+1 » : `@EntityGraph` sur les listes (jointures), `@BatchSize` et `hibernate.default_batch_fetch_size=50` pour les collections.
- Listes paginées pour les réservations et les clients ; statistiques calculées par `COUNT` / `SUM` en base.
- Index `idx_reservation_dispo (id_vehicule, statut, date_debut, date_fin)` pour la vérification de disponibilité.
- `spring.jpa.open-in-view=false` : la connexion à la base est rendue dès la fin du service.
- Compression gzip des réponses, cache navigateur de 30 jours pour le CSS et le JS (versionnés `?v=N`).
- Logs SQL uniquement dans le profil `dev`.

## Monitoring (Prometheus + Grafana)
1. Lancer l'application (port 8081), puis Docker Desktop.
2. Dans `autoloc-api/` : `docker compose up -d`.
3. Prometheus : http://localhost:9090 → *Status → Targets* (`autoloc-api` **UP**).
4. Grafana : http://localhost:3000 (`admin` / `admin`, puis changer le mot de passe) → tableau de bord **AutoLoc — Application Monitoring**
   (déjà installé : mémoire JVM, CPU, requêtes/s, temps de réponse p95, threads, connexions MySQL, erreurs 5xx…).
5. Trafic de test : `.\monitoring\generate-traffic.ps1`. Arrêt : `docker compose down`.

Détails, métriques et problème résolu : [`autoloc-api/docs/monitoring-notes.md`](autoloc-api/docs/monitoring-notes.md).

## Configuration
| Fichier | Rôle |
|---|---|
| `application.properties` | Configuration commune (MySQL via `DB_USERNAME` / `DB_PASSWORD`, port **8081**, sécurité, performances). |
| `application-dev.properties` | Profil par défaut : données de démo, logs SQL (`show-sql`, `format_sql`, DEBUG). |
| `application-prod.properties` | Profil de production (voir Sécurité). |

## Lancer la version Java simple
IntelliJ → *File → Open…* → `java-simple`, marquer `src` comme *Sources Root* si besoin, puis lancer `Main`.
