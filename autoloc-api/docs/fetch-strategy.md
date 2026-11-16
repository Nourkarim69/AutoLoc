# Stratégie de chargement (fetch) et de cascade — AutoLoc

Ce document justifie, association par association, les choix de **fetch** (LAZY / EAGER) et de **cascade**
faits dans le package `fr.ece.autoloc.domain` (Workshop 2).

## Règles générales

1. **Toutes les associations sont LAZY**, comme recommandé par le TP :
   `@ManyToOne` et `@OneToOne` (EAGER par défaut en JPA) ont été passés explicitement en `FetchType.LAZY`
   pour éviter de charger des chaînes d'objets (réservation → client, voiture → agence → …) dont on n'a pas besoin.
2. **Quand l'API a besoin d'une association, elle la demande explicitement** avec `@EntityGraph`
   (une seule requête SQL avec des `LEFT JOIN`), sur les méthodes des repositories qui alimentent le JSON :
   `findAll`, `findById`, `findByClientIdClientOrderByDateDebutDesc`, etc. C'est la « requête de fetch explicite »
   conseillée par le TP. Comme `spring.jpa.open-in-view=false`, aucune donnée n'est chargée après la fin du service.
3. **La cascade n'est utilisée que lorsque l'enfant dépend du cycle de vie du parent.** Une entité qui vit
   seule (voiture, client, équipement, agence) n'est jamais supprimée par cascade.
4. Les collections inverses (`@OneToMany(mappedBy = …)`) sont annotées `@JsonIgnore` pour éviter les boucles JSON,
   et Lombok reste ciblé (`@Getter` / `@Setter`, jamais `@Data`).

## Tableau des associations

| Association | Mapping / côté propriétaire (clé étrangère) | Fetch | Cascade | Justification |
|---|---|---|---|---|
| Contrat → Paiements | `@OneToMany(mappedBy)` / `@ManyToOne` ; **Paiement** possède `id_contrat` | LAZY | **ALL + orphanRemoval** | Un paiement n'existe pas sans son contrat (composition) : il est enregistré et supprimé avec lui, et retiré de la liste = supprimé. |
| Agence → Véhicules | `@OneToMany(mappedBy)` / `@ManyToOne` ; **Véhicule** possède `id_agence` | LAZY | aucune | Une voiture survit à la suppression de son agence et peut changer d'agence. |
| Agence → Employés | `@OneToMany(mappedBy)` / `@ManyToOne` ; **Employé** possède `id_agence` | LAZY | aucune | Un employé est géré seul (création, changement d'agence). |
| Véhicule ↔ Équipement | `@ManyToMany`, **Véhicule** possède la table de jointure `vehicule_equipement` | LAZY | aucune | Catalogue d'équipements partagé : il ne doit jamais disparaître avec une voiture. `Set` pour éviter les doublons. |
| Client → Réservations | `@OneToMany(mappedBy)` / `@ManyToOne` ; **Réservation** possède `id_client` | LAZY | **PERSIST** | Une nouvelle réservation ajoutée à un nouveau client est enregistrée avec lui. Pas de REMOVE : l'historique des réservations est conservé. |
| Réservation → Véhicule | `@ManyToOne` ; **Réservation** possède `id_vehicule` | LAZY | aucune | Supprimer une réservation ne doit jamais supprimer la voiture. |
| Réservation ↔ Contrat | `@OneToOne` ; **Contrat** possède `id_reservation` (unique) ; côté inverse `mappedBy` dans Réservation | LAZY | **ALL côté Réservation** | Le contrat suit la vie de sa réservation (créé à la confirmation, supprimé avec elle). |
| Véhicule → Maintenances | `@OneToMany(mappedBy)` / `@ManyToOne` ; **Maintenance** possède `id_vehicule` | LAZY | **PERSIST** | Une nouvelle maintenance ajoutée à la voiture est enregistrée avec elle. Pas de REMOVE : l'historique d'entretien est gardé (une voiture déjà passée au garage ne peut pas être supprimée par erreur : HTTP 409). |
| Réservation ↔ Équipement (options choisies) | `@ManyToMany`, **Réservation** possède `reservation_equipement` | **EAGER + `@BatchSize(50)`** | aucune | Ajout propre à AutoLoc (options de location). Les options font partie du JSON d'une réservation ; `@BatchSize` les charge pour 50 réservations en une requête (pas de N+1). Exception assumée à la règle « collections LAZY ». |

## Points d'attention

- **`@OneToOne` inverse et LAZY.** Côté `Reservation.contrat` (`mappedBy`), Hibernate ne peut pas savoir
  sans requête si un contrat existe : sans *bytecode enhancement*, ce côté est donc chargé à la lecture
  de la réservation. Pour ne pas créer une requête par réservation, les listes de réservations ajoutent
  `contrat` à leur `@EntityGraph` (même requête, `LEFT JOIN`).
- **Deux côtés à jour.** Quand on crée un contrat, on renseigne les deux côtés
  (`contrat.setReservation(r)` et `r.setContrat(contrat)`), comme le demande le TP pour Contrat ↔ Paiement.
- **`LazyInitializationException` évitée** : les écritures des contrôleurs (`VehiculeController`,
  `EmployeController`) et des services sont `@Transactional`, et les entités renvoyées en JSON sont lues avec un
  `@EntityGraph`. Les entités portent aussi `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})`
  pour qu'un proxy Hibernate ne casse jamais la sérialisation.
- **Paiement → Contrat** n'est pas renvoyé en JSON (`@JsonIgnore`) : le JSON d'un paiement ne contient que
  `idContrat`, lu sur le proxy sans requête SQL.

## Vérification LAZY / EAGER (étape 4 du TP)

Avec `spring.jpa.show-sql=true` (profil `dev`, logs de la console IntelliJ), lire un contrat par son id
(`contratRepository.findById`) :

- **LAZY** (configuration finale) : une requête `select … from contrat … left join reservation … left join client …`
  grâce à l'`@EntityGraph`, et **rien** sur la table `paiement` tant que la liste des paiements n'est pas lue.
- **EAGER** (essai temporaire sur `Contrat.paiements`, puis retour à LAZY) : les paiements sont chargés à
  **chaque** lecture du contrat (jointure ou requête en plus sur `paiement`), même quand on ne les affiche pas.

Mesures chiffrées du problème N+1 : `PerformanceBenchmark` (dossier `src/test/java/fr/ece/autoloc/perf`)
et le rapport `rapport/Performance_AutoLoc.pdf`.
