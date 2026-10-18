# God Mod — simulation de dieux (Forge 1.21.11)

Chaque joueur commence **mortel**, gagne en puissance physique et mentale, puis devient **dieu** :
il convertit des fidèles par le PvP, soumet d'autres dieux, bâtit son monde divin, mène des guerres
et doit survivre aux rébellions de ses propres fidèles.

- Minecraft **1.21.11**, Forge **61.2.x**, Java 21
- **Mod côté serveur uniquement** : les joueurs se connectent avec un Minecraft 1.21.11 normal, sans rien installer
- Tout démarre automatiquement avec le serveur (données, structuration du monde, authentification)
- Interface : `/dieu` (ou clic droit avec le **Codex divin**, donné à la première connexion ; `/dieu codex` s'il est perdu)

## Installation

1. Serveur **Forge 1.21.11**.
2. Déposer `godmod-x.y.z.jar` dans le dossier `mods/` du serveur, puis démarrer.
3. Se donner les droits : `op TonPseudo` dans la console (les opérateurs sont admins du mod).

Au **premier démarrage**, le mod construit automatiquement (en arrière-plan, sans bloquer le serveur) :
- le **Sanctuaire**, temple céleste sur une île flottante ~50 blocs au-dessus du spawn : place en mosaïque
  quartz/or/violet, fontaine surmontée d'un trident d'or et d'une spirale d'eau, temple à colonnade et bannières
  violettes, portail-vortex, faisceau violet, monuments Océan / Ciel / Cosmos, jardin et petites îles autour ;
- l'**île de l'Arène des tournois**, reliée au Sanctuaire par un pont ;
- la **dimension divine** (`godmod:divine`) et le **monde ressource** (`godmod:ressources`, Overworld classique).

Le Sanctuaire est protégé (pas de construction, de seaux, de feu, d'explosions ni de monstres) ; une chute depuis
les îles ramène au Sanctuaire. Un Sanctuaire v0.2 (au sol) est automatiquement démonté et remplacé.
`/structurer` (admin) reconstruit le Sanctuaire au-dessus de votre position.

## Déplacements

- `/spawn` : Sanctuaire · `/mine` : monde ressource (endroit aléatoire et sûr) · `/monde` : votre monde divin.
- `/setwarp <nom>` : warp public à votre position (1 pour un mortel → 10 pour un dieu suprême) ·
  `/warp [nom]` (sans nom : liste cliquable) · `/warps` · `/delwarp <nom>`.

## Monde divin

- **Réservé aux dieux** : un mortel n'en a pas ; il le reçoit en passant Dieu.
- Chaque monde a une **bordure visible** (world border) à sa taille. Impossible de la franchir : on est simplement
  repoussé à sa dernière position. Seule une chute dans le vide ramène au centre.
- Les pistons ne peuvent pas faire passer de blocs de l'autre côté de la bordure.
- Visiteurs : seigneurs, fidèles, ennemis en guerre, et tout le monde si le dieu y a posé un warp.

## Codex divin et rangs

- Le Codex reste dans la **9ᵉ case de la barre rapide** : impossible de le jeter, de le ranger ou de le perdre.
- La liste des joueurs (Tab) et le chat affichent le rang : `[Admin]`/`[Modo]` puis le palier (`[✦ Dieu]`…).

## Comptes protégés par mot de passe

À chaque connexion, le joueur est envoyé au Sanctuaire, figé, aveuglé et invulnérable jusqu'à :
- `/register <motdepasse> <motdepasse>` la première fois ;
- `/login <motdepasse>` ensuite (raccourci `/l`).

Après `/login`, il retrouve l'endroit où il s'était déconnecté. Chat, commandes et interactions sont bloqués avant connexion. 3 erreurs ou 90 s sans connexion = expulsion.
Reconnexion automatique depuis la même adresse IP pendant 12 h (`/auth session <heures>`, 0 = jamais).
Mots de passe hachés (PBKDF2-SHA256 salé), jamais stockés en clair. `/motdepasse <ancien> <nouveau>` pour changer.
Admins : `/auth on|off`, `/auth reset <joueur>`.

> Utile surtout si le serveur est en `online-mode=false`. En `online-mode=true`, Mojang vérifie déjà les comptes :
> le mot de passe ajoute une seconde protection.

## Équipe : modérateurs et admins

- **Admin** : opérateurs du serveur + joueurs nommés par `/staff ajouter <joueur> admin`.
- **Modérateur** : `/staff ajouter <joueur> moderateur` · `/staff retirer <joueur>` · `/staff liste`.

| Commande | Modérateur | Admin |
|---|---|---|
| `/mod kick <joueur> [raison]` | ✔ | ✔ |
| `/mod mute <joueur> <minutes> [raison]` · `/mod unmute <joueur>` | ✔ | ✔ |
| `/mod ban <joueur> <heures> [raison]` (0 = définitif) | 72 h max | ✔ |
| `/mod unban` · `/mod warn` · `/mod freeze` · `/mod tp` · `/mod info` | ✔ | ✔ |
| `/mod journal [lignes]` · `/sc <message>` (chat d'équipe) | ✔ | ✔ |
| `/tournoi creer [prix]` · `/tournoi annuler` | ✔ | ✔ |
| `/delwarp` sur n'importe quel warp | ✔ | ✔ |
| `/staff`, `/auth`, `/structurer`, `/godadmin`, `/pay` | | ✔ |
| `/godadmin clearile <joueur>` (remet un monde divin à zéro) · `/godadmin clearzone <rayon> [hauteur]` | | ✔ |

Tout joueur peut faire `/signaler <joueur> <raison>` : l'équipe connectée est prévenue.
Un membre de l'équipe ne peut pas sanctionner quelqu'un de rang supérieur.

## Compiler le .jar avec GitHub (développeurs)

1. Envoyez tout le contenu de ce dossier dans le dépôt `God-mod` (racine du dépôt).
2. Le workflow `.github/workflows/build.yml` se lance à chaque push.
3. Onglet **Actions** → dernière exécution → **Artifacts** → `godmod-jar`.
4. Placez le .jar dans le dossier `mods/` du serveur (inutile chez les joueurs).

En local : `./gradlew build` (le .jar est dans `build/libs/`).

## Progression

| Palier | Essence | Condition | Plafond de caractéristique |
|---|---|---|---|
| Mortel | 0 | — | 3 |
| Éveillé | 250 | — | 6 |
| Demi-dieu | 1 000 | peut convertir (3 fidèles max) | 10 |
| Dieu | 3 000 | 1 fidèle direct | 15 |
| Dieu suprême | 12 000 | 8 fidèles au total | 25 |

**Essence** : temps de jeu (2/min), monstres (1–3), victoires PvP (25), conversions (60),
déicide (500), tournoi gagné (300).
**Caractéristiques** (1 point / 200 Essence + 3 par palier) : Force (dégâts), Vitalité (vie),
Agilité (vitesse), Esprit (Foi max, régénération, puissance et recharge des artefacts).
**Foi** : ressource des pouvoirs, régénérée par le temps, l'Esprit et le nombre de fidèles.

## Règles divines

- **Conversion** : un demi-dieu ou un dieu qui tue un joueur moins puissant en fait son fidèle.
  Le fidèle reverse 10 % de ses gains à son dieu (dîme). Les dieux vaincus deviennent vassaux.
- **Déicide** : un non-dieu qui tue un dieu le fait chuter (redevient mortel, −50 % d'Essence).
  Le vainqueur choisit : **devenir dieu et l'asservir** (avec ses fidèles) ou refuser.
- **Rébellion** (`/rebellion`) : un fidèle se soulève contre son dieu (une fois tous les 3 jours
  par joueur), 60 s de ralliement, 10 min de combat. Réussite : le dieu chute et le meneur peut
  prendre le trône. Échec : −30 % d'Essence, −25 % d'argent, Parjure 3 jours (stats ÷ 2).
- **Dieu inactif** (7 jours) : `/trone reclamer <dieu>` lance un tournoi pour son trône.
- **Tournoi** : duels 1v1 en poules (≤ 6 joueurs : une poule ; au-delà : poules de 3–5 puis
  élimination directe). Morts annulées pendant les duels, 3 min max par duel.
- **Guerre** (`/guerre`) : 30 min. Le dieu adverse tué → son panthéon entier est soumis,
  et les mondes divins des deux camps peuvent être saccagés pendant la guerre.

## Mondes

- **Monde principal** : ressources.
- **Monde divin** (`/monde`) : dimension vide `godmod:divine`, une parcelle par joueur.
  Elle démarre avec un bloc de terre **indestructible tant qu'aucun bloc n'est posé**.
  Rayon = 16 + 8 × fidèles (total) + 8 × palier, plafonné à 400.
- **Nether et End bloqués** : leurs items sont en vente au shop (`/godadmin netherend` pour changer).

## Boutique

`/shop` : Nether, End, ressources, artefacts. Revente 5 à 10 fois moins chère que l'achat.
**Premium** (Gemmes, attribuées par un admin) : désactivé par défaut, `/pay enable` / `/pay disable`.

## Artefacts (réservés aux dieux du bon domaine)

Les artefacts sont des objets vanilla enchantés et renommés, reconnus par le serveur (non revendables).

| Artefact | Domaine | Effet | Foi |
|---|---|---|---|
| Trident des Marées (trident) | Océan | Vague qui repousse, blesse et ralentit | 30 |
| Sceptre de Foudre (bâton de breeze) | Ciel | Foudre là où vous visez (plus d'éclairs avec le palier) | 40 |
| Orbe du Néant (éclat d'écho) | Cosmos | Trou noir qui aspire puis implose | 60 |

## Commandes

Joueurs : `/dieu` · `/dieu profil [joueur]` · `/dieu stats <carac>` · `/dieu specialisation <ocean|ciel|cosmos>`
· `/dieu pouvoir <benir|convoquer|chatier|temps|espace>` · `/dieu fideles` · `/dieu liberer <joueur>`
· `/dieu choix <trone|refuser>` · `/monde [quitter|visiter <joueur>]` · `/shop [vendre [un]]` · `/argent`
· `/rebellion [declarer|rejoindre]` · `/tournoi [rejoindre|quitter]` · `/trone reclamer <dieu>`
· `/guerre [declarer <dieu>]` · `/classement`

Admins :
`/godadmin voir <joueur>` · `essence|argent|gemmes <joueur> <valeur>` · `palier <joueur> <palier>`
· `maitre <joueur> <maître|aucun>` · `reset <joueur>` · `arene` · `mode <serveur|petit>`
· `cooldown <heures>` · `inactivite <jours>` · `netherend <bloquer|autoriser>` · `journal [lignes]`
· `/pay <enable|disable|status>`

`/godadmin mode petit` adapte le mod à une petite survie (seuils ×0,25, Essence ×2,
rébellion toutes les 12 h, inactivité 2 jours).

Les données sont sauvegardées dans le monde (`world/data/godmod.dat`). Le journal
(`/godadmin journal`) trace conversions, chutes, rébellions, achats premium et actions admin.
