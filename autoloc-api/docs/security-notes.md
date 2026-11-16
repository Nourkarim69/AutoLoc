# Notes de sécurité — Spring Security dans AutoLoc

## Ce qui a été ajouté (TP « Secure AutoLoc with Spring Security »)

- Dépendance `spring-boot-starter-security` dans `pom.xml`.
- `fr.ece.autoloc.security.SecurityConfig` : un **`SecurityFilterChain`**, l'authentification **HTTP Basic**,
  et un `InMemoryUserDetailsManager` avec deux comptes de démonstration :

  | Compte | Mot de passe (classe uniquement) | Rôle |
  |---|---|---|
  | `student` | `student123` (variable `DEMO_USER_PASSWORD`) | USER |
  | `admin` | `admin123` (variable `DEMO_ADMIN_PASSWORD`) | ADMIN |

- `SecurityDemoController` : `/api/public/ping`, `/api/hello`, `/api/admin/report`.

## Règles des routes

```java
.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
.requestMatchers("/api/public/**").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/hello").hasAnyRole("USER", "ADMIN")
.anyRequest().permitAll()   // le reste de l'API : contrôlé par AuthInterceptor (voir plus bas)
```

Résultats vérifiés avec `curl` (Postman : *Authorization → Basic Auth*) :

| Requête | Anonyme | USER (`student`) | ADMIN (`admin`) |
|---|---|---|---|
| `GET /api/public/ping` | 200 | 200 | 200 |
| `GET /api/hello` | **401** | 200 | 200 |
| `GET /api/admin/report` | **401** | **403** | 200 |

Les réponses 401 / 403 sont en JSON (`{"status":401,"message":"Authentification requise"}`), comme le reste de l'API.

## Réponses aux questions du TP

**Pourquoi `/api/admin/**` doit-il être déclaré avant `/api/**` ?**
Spring Security applique la **première règle qui correspond**. Si `/api/**` (USER ou ADMIN) était déclarée
en premier, `/api/admin/report` correspondrait déjà à cette règle et un simple USER y aurait accès : la règle
ADMIN ne serait jamais lue. On déclare donc toujours les règles **de la plus précise à la plus large**.

**Différence entre 401 et 403 ?**
- **401 Unauthorized** = « je ne sais pas qui tu es » : pas d'identifiants, ou identifiants faux.
  La réponse contient `WWW-Authenticate: Basic` pour demander de s'authentifier.
- **403 Forbidden** = « je sais qui tu es, mais tu n'as pas le droit » : `student` est bien authentifié
  mais n'a pas le rôle ADMIN.

**Pourquoi BCrypt plutôt que le mot de passe en clair ?**
Si la base (ou la mémoire) fuit, un hash BCrypt ne permet pas de retrouver le mot de passe : il est
**à sens unique**, **salé** (deux mots de passe identiques donnent deux hashs différents, donc pas de
tables précalculées) et **volontairement lent** (coût 12 ≈ 0,4 s par essai), ce qui rend la force brute très coûteuse.

**Pourquoi des utilisateurs en mémoire conviennent à l'exercice, mais pas à la production ?**
Ils sont écrits dans le code / la configuration : on ne peut pas créer de compte, changer un mot de passe
ou révoquer un accès sans redéployer, ils disparaissent à l'arrêt, et ils ne sont pas partagés entre plusieurs
serveurs. Leurs mots de passe ne doivent jamais être de vrais mots de passe commités dans Git.

**Que faudrait-il pour charger les utilisateurs et les rôles depuis MySQL ?**
Une table des utilisateurs avec un mot de passe haché et un rôle (AutoLoc l'a déjà : `client` et `employe`),
un `UserDetailsService` qui lit cette table via un repository (`findByEmail`) et renvoie un `UserDetails`
avec le hash BCrypt et le rôle, et le même `PasswordEncoder` BCrypt. Spring Security compare alors lui-même
le mot de passe saisi au hash stocké.

## Comment Spring Security s'intègre au site AutoLoc

AutoLoc avait déjà sa propre connexion pour le site : `POST /api/auth/login`, mot de passe vérifié avec BCrypt,
utilisateur gardé dans la **session HTTP** (cookie `HttpOnly`, `SameSite=Strict`), anti force brute
(5 échecs → 15 min de blocage), et `AuthInterceptor` qui vérifie les droits route par route
(un client ne voit que **ses** réservations).

Pour que les deux mécanismes travaillent ensemble :

1. **`SessionAuthenticationFilter`** fait le pont : si la requête a une session du site, l'utilisateur
   devient l'utilisateur Spring Security pour cette requête (client → `ROLE_USER`, employé → `ROLE_ADMIN`).
   Un client connecté sur le site reçoit donc 200 sur `/api/hello` et 403 sur `/api/admin/report`.
2. `/api/admin/**` est protégé **deux fois** : par Spring Security (rôle ADMIN) et par `AuthInterceptor`.
3. Les comptes en mémoire `student` / `admin` ne servent qu'aux routes de démonstration (`/api/public`,
   `/api/hello`, `/api/admin/report`) ; le reste de l'API reste protégé par la session et `AuthInterceptor`.

## Autres réglages

- **CSRF désactivé**, choix réfléchi : l'API est en JSON, le cookie de session est `SameSite=Strict`
  (un autre site ne peut pas l'envoyer, c'est exactement ce contre quoi protège le jeton CSRF), et les
  identifiants HTTP Basic ne sont jamais envoyés automatiquement par une autre page.
- **En-têtes de sécurité** de Spring Security gardés (`X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`) ; seul le `Cache-Control: no-cache` est désactivé pour garder le cache de 30 jours du CSS / JS.
- Le défi `WWW-Authenticate: Basic` n'est envoyé que sur `/api/hello` et `/api/admin/report`, pour que
  le navigateur n'affiche pas de fenêtre de connexion sur les pages du site.
- **Actuator** : seuls `health` et `prometheus` sont exposés et publics (pour Prometheus) ; tout autre
  endpoint Actuator demande le rôle ADMIN. En production (`prod`), le détail de `health` n'est montré qu'aux
  utilisateurs autorisés.

## Limites connues

- HTTP Basic renvoie les identifiants à **chaque** requête : il faut HTTPS en production (profil `prod`, voir
  `application-prod.properties`), et chaque requête refait un calcul BCrypt (≈ 0,4 s, visible dans Grafana
  sur le temps de réponse p95 de `/api/hello`). Pour une vraie API, on utiliserait une session ou un jeton.
