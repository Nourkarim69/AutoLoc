# AutoLoc — ECE UP ASI (Architecture des Systèmes d'Information)

Projet d'agence de location de véhicules « AutoLoc », construit au fil des workshops ASI :
une API REST Spring Boot + MySQL et un site web (servi par la même application).

| Dossier | Contenu |
|---|---|
| `autoloc-api/` | Application Spring Boot + Maven + Spring Data JPA + Lombok (API REST + site web). |
| `postman/` | Collection Postman de toutes les routes de l'API. |
| `uml/location.mdj` | Diagramme de classes StarUML : `Client` / `Vehicule`, association `louer` (0..1 / 0..*). |
| `java-simple/` | Version Java simple de ce diagramme (sans framework), package `modele` + `Main`. |

## Avancement

| Étape | Contenu |
|---|---|
| **Workshop 1** | Projet Spring Boot, connexion MySQL, entité `Vehicule`, puis les 8 autres entités et 5 enums (sans associations). Bonus : `CommandLineRunner` + profil `dev`. |
| **Workshop 2** | Associations, cascade et fetch (voir le tableau plus bas). |
| **API REST** | Contrôleurs `web.controller`, DTO validés `web.dto`, logique métier `service`, erreurs JSON en français. |
| **Sécurité** | Mots de passe BCrypt, session, rôles CLIENT / ADMIN, anti force brute, changement / réinitialisation du mot de passe, profil `prod`. |
| **Site web** | Accueil, catalogue, réservation, agences, connexion, espace client, administration. |
| **Tests** | 40 tests JUnit 5 (règles de réservation, droits d'accès, anti force brute). |

## Prérequis
- JDK 17+
- IntelliJ IDEA (plugin Lombok + *Enable annotation processing*)
- MySQL (ou MariaDB) démarré sur `localhost:3306` (par exemple XAMPP → *Start* sur la ligne MySQL)
- Postman (facultatif)

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

| Association | Cardinalité | Côté propriétaire (clé étrangère) | Fetch | Cascade | Justification |
|---|---|---|---|---|---|
| `Vehicule` → `Agence` | 0..* → 0..1 | `vehicule.id_agence` | EAGER | aucune | L'agence est affichée avec la voiture ; une voiture peut changer d'agence. |
| `Agence` → `vehicules` | 1 → 0..* | `mappedBy` | LAZY | aucune | Jamais nécessaire à la lecture d'une agence ; supprimer une agence ne doit pas supprimer ses voitures. |
| `Employe` → `Agence` | 0..* → 0..1 | `employe.id_agence` | EAGER | aucune | Agence de rattachement de l'employé. |
| `Reservation` → `Client` | 0..* → 1 | `reservation.id_client` | EAGER | aucune | Supprimer une réservation ne doit jamais supprimer le client. |
| `Reservation` → `Vehicule` | 0..* → 1 | `reservation.id_vehicule` | EAGER | aucune | Idem pour le véhicule. |
| `Reservation` ↔ `Equipement` | * ↔ * | table `reservation_equipement` | EAGER + `@BatchSize(50)` | aucune | Catalogue partagé d'options ; le batch évite une requête par réservation. |
| `Contrat` → `Reservation` | 0..1 → 1 | `contrat.id_reservation` (unique) | EAGER | aucune | Un contrat par réservation confirmée. |
| `Contrat` → `paiements` | 1 → 0..* | `mappedBy` | LAZY | ALL + `orphanRemoval` | Composition : les paiements n'existent pas sans leur contrat. |
| `Paiement` → `Contrat` | 0..* → 1 | `paiement.id_contrat` | EAGER | aucune | Le contrat cascade vers ses paiements, pas l'inverse. |
| `Vehicule` → `maintenances` | 1 → 0..* | `mappedBy` | LAZY | REMOVE + `orphanRemoval` | L'historique de maintenance est supprimé avec la voiture. |
| `Maintenance` → `Vehicule` | 0..* → 1 | `maintenance.id_vehicule` | EAGER | aucune | Ouvrir une maintenance passe la voiture en `MAINTENANCE`, la terminer la rend `DISPONIBLE`. |

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
| Public | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/vehicules[/{id}]`, `GET /api/agences[/{id}]`, `GET /api/equipements`, `GET /api/reservations/indisponibilites?vehicule={id}` |
| Connecté | `PATCH /api/auth/password`, `POST /api/reservations`, `GET /api/reservations/mine`, `PATCH /api/reservations/{id}/prolonger`, `PATCH /api/reservations/{id}/annuler`, `GET /api/contrats/mine`, `GET /api/contrats/{id}/paiements` |
| Admin | `GET /api/admin/stats`, `GET /api/reservations?page=&size=`, `PATCH /api/reservations/{id}/statut`, `GET /api/contrats`, `POST /api/contrats/{id}/paiements`, `PATCH /api/contrats/{id}/invalider`, CRUD `/api/vehicules`, `/api/agences`, `/api/equipements`, `/api/employes`, `/api/maintenances` (+ `PATCH /{id}/terminer`), `GET /api/clients?page=&size=`, `DELETE /api/clients/{id}`, `POST /api/clients/{id}/reinitialiser-mot-de-passe` |

Les erreurs sont renvoyées en JSON : `{"status": 400, "message": "...", "errors": {"champ": "..."}}`
(400 donnée invalide, 401 non connecté, 403 interdit, 404 introuvable, 409 conflit, 429 trop de tentatives).

## Sécurité
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

## Configuration
| Fichier | Rôle |
|---|---|
| `application.properties` | Configuration commune (MySQL via `DB_USERNAME` / `DB_PASSWORD`, port **8081**, sécurité, performances). |
| `application-dev.properties` | Profil par défaut : données de démo, logs SQL (`show-sql`, `format_sql`, DEBUG). |
| `application-prod.properties` | Profil de production (voir Sécurité). |

## Lancer la version Java simple
IntelliJ → *File → Open…* → `java-simple`, marquer `src` comme *Sources Root* si besoin, puis lancer `Main`.
