# AutoLoc — ECE UP ASI (Architecture des Systèmes d'Information)

Projet d'agence de location de véhicules « AutoLoc », construit au fil des workshops ASI.

| Dossier | Contenu |
|---|---|
| `autoloc-api/` | **Workshop 1** : projet Spring Boot + Maven + Spring Data JPA + Lombok, connecté à MySQL. 9 entités JPA (`Vehicule`, `Agence`, `Client`, `Employe`, `Equipement`, `Reservation`, `Contrat`, `Paiement`, `Maintenance`) et 5 enums, sans associations pour l'instant (elles arrivent au Workshop 2). |
| `uml/location.mdj` | Diagramme de classes StarUML : `Client` / `Vehicule`, association `louer` (0..1 / 0..*). |
| `java-simple/` | Version Java simple de ce diagramme (sans framework), package `modele` + `Main`. |

## Prérequis
- JDK 17+
- IntelliJ IDEA
- MySQL démarré sur `localhost:3306` (par exemple XAMPP → *Start* sur la ligne MySQL)

## Lancer le projet Spring Boot
```bash
git clone https://github.com/Nourkarim69/AutoLoc.git
```
1. IntelliJ → *File → Open…* → choisir le dossier **`autoloc-api`** (pas la racine du dépôt). Maven télécharge les dépendances.
2. Démarrer MySQL. La base `autoloc_db` est créée automatiquement (`createDatabaseIfNotExist=true`).
3. Lancer `AutolocApiApplication` (bouton ▶ vert).
4. Dans la console : `Started AutolocApiApplication`. Hibernate crée les 9 tables et le profil `dev` insère 3 véhicules de démo.

En ligne de commande, dans `autoloc-api/` : `mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run`.

### Configuration (`autoloc-api/src/main/resources/application.properties`)
- Utilisateur / mot de passe MySQL lus dans les variables d'environnement `DB_USERNAME` / `DB_PASSWORD` (par défaut `root` / vide, comme XAMPP).
- Port du serveur : **8081** (modifier `server.port` si besoin).
- Profil actif : `dev` (données de démo). Variable `SPRING_PROFILES_ACTIVE` pour le changer.

## Lancer la version Java simple
IntelliJ → *File → Open…* → `java-simple`, marquer `src` comme *Sources Root* si besoin, puis lancer `Main`.
