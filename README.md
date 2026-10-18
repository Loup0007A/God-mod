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

Au **premier démarrage**, le mod construit automatiquement (en arrière-plan, sans bloquer le serveur)
le **Sanctuaire des Dieux** dans sa propre dimension vide `godmod:spawn` :
- grande île flottante aux falaises de pierre, cascades qui tombent dans le vide, jardins de cerisiers et d'azalées ;
- place en rose des vents (quartz, or, violet), fontaine à deux vasques et trident d'or géant entouré d'une spirale ;
- temple du Panthéon : escalier monumental, colonnade, bannières violettes, fronton, tours d'améthyste,
  **portail-vortex** (vers votre monde divin) et faisceau violet ;
- **portails** : monde ressource (arche d'émeraude à l'ouest), pavillons **Boutique** et **Warps**, pont vers l'**Arène** ;
- panneaux flottants pour se repérer, îles satellites.

Chaque connexion, chaque réapparition après une mort et `/spawn` mènent au Sanctuaire.
Il est entièrement protégé : impossible de casser, poser, utiliser seaux/feu ou d'y combattre (sauf duels),
aucun monstre, aucune explosion. Tomber dans le vide ramène au point d'apparition.

Admins : `/godadmin spawn reconstruire` · `/godadmin spawn vider confirmer` (tout effacer pour bâtir le vôtre) ·
`/godadmin spawn edition` (activer/désactiver le droit de modifier) · `/godadmin spawn definir` (point d'apparition).

## Aide en jeu

`/aide` affiche toutes les commandes par thème (Essentiel, Progression, Pouvoirs, Mondes, Économie, Conflits,
Compte, et Modération / Administration pour l'équipe). Chaque commande est **cliquable** (elle se pré-remplit
dans le chat) et son survol l'explique. La liste s'adapte au rang du joueur.

## Déplacements

- `/spawn` : Sanctuaire · `/mine` : monde ressource (endroit aléatoire et sûr) · `/monde` : votre monde divin.
- `/setwarp <nom>` : warp public à votre position (1 pour un mortel → 10 pour un dieu suprême) ·
  `/warp [nom]` (sans nom : liste cliquable) · `/warps` · `/delwarp <nom>`.
- `/tpa <joueur>` (aller vers lui) · `/tpahere <joueur>` (le faire venir) · `/tpaccept` · `/tpdeny`
  (boutons cliquables dans le chat) · `/tpatoggle` (refuser toutes les demandes). La destination doit être
  accessible au voyageur (permissions des îles respectées).
- **3 secondes sans bouger** avant chaque téléportation par commande (annulée si l'on bouge ou si l'on est
  blessé), et **aucune téléportation pendant 12 s après un coup reçu ou donné à un joueur** : on ne fuit plus
  un combat avec `/spawn`. Départ immédiat depuis le Sanctuaire et pour l'équipe.
  Réglable : `/godadmin teleport <secondes>` (0 = immédiat).

## Monde divin

- **Réservé aux dieux** : un mortel n'en a pas ; il le reçoit en passant Dieu.
- Chaque monde a une **bordure visible** (world border) à sa taille. Impossible de la franchir : on est simplement
  repoussé à sa dernière position. Seule une chute dans le vide ramène au centre.
- Les pistons ne peuvent pas faire passer de blocs de l'autre côté de la bordure.
- **Permissions de l'île** (`/ile` ou le menu) : pour vos **fidèles**, les **visiteurs** et chaque joueur ajouté,
  activez ou non : Entrer, Casser, Poser, Interagir (portes, leviers…), Coffres. `/ile ajouter <joueur>` donne tout,
  `/ile retirer <joueur>`, `/ile perm <joueur|fideles|visiteurs> <perm> <on|off>`, `/ile pvp <on|off>`.
  Vos seigneurs et les ennemis en guerre gardent tous les droits.

## Codex divin et rangs

- Le Codex reste dans la **9ᵉ case de la barre rapide** : impossible de le jeter, de le ranger ou de le perdre.
- La liste des joueurs (Tab) et le chat affichent le rang : `[Admin]`/`[Modo]` puis le palier (`[✦ Dieu]`…).

## Comptes protégés par mot de passe

À **chaque** connexion, le joueur est envoyé au Sanctuaire, figé, aveuglé et invulnérable jusqu'à :
- `/register <motdepasse> <motdepasse>` la première fois ;
- `/login <motdepasse>` ensuite (raccourci `/l`).

Chat, commandes et interactions sont bloqués avant connexion. 3 erreurs ou 90 s sans connexion = expulsion.
Le mot de passe est demandé à chaque connexion (`/auth session <heures>` pour autoriser une reconnexion automatique depuis la même IP).
Mots de passe hachés (PBKDF2-SHA256 salé), jamais stockés en clair. `/motdepasse <ancien> <nouveau>` pour changer.
Admins : `/auth on|off`, `/auth reset <joueur>`.

> Utile surtout si le serveur est en `online-mode=false`. En `online-mode=true`, Mojang vérifie déjà les comptes :
> le mot de passe ajoute une seconde protection.

## Équipe : modérateurs et admins

- **Admin** : opérateurs du serveur + joueurs nommés par `/staff ajouter <joueur> admin`.
- **Modérateur** : `/modo ajouter <joueur>` (ou `/staff ajouter <joueur> moderateur`) ·
  `/modo admin <joueur>` · `/modo retirer <joueur>` · `/modo liste`. Le menu **Équipe** de `/dieu`
  permet aussi de nommer les joueurs connectés (clic gauche = modérateur, droit = admin, maj = joueur).

| Commande | Modérateur | Admin |
|---|---|---|
| `/mod kick <joueur> [raison]` | ✔ | ✔ |
| `/mod mute <joueur> <minutes> [raison]` · `/mod unmute <joueur>` | ✔ | ✔ |
| `/mod ban <joueur> <heures> [raison]` (0 = définitif) | 72 h max | ✔ |
| `/mod unban` · `/mod warn` · `/mod freeze` · `/mod tp` · `/mod tphere` · `/mod info` | ✔ | ✔ |
| `/mod invsee <joueur>` · `/mod ecsee <joueur>` (inventaire / coffre de l'Ender, lecture seule) · `/argent <joueur>` | ✔ | ✔ |
| `/mod journal [lignes]` · `/sc <message>` (chat d'équipe) | ✔ | ✔ |
| `/tournoi creer [prix]` · `/tournoi annuler` | ✔ | ✔ |
| `/delwarp` sur n'importe quel warp | ✔ | ✔ |
| `/staff`, `/auth`, `/structurer`, `/godadmin`, `/pay` | | ✔ |
| `/mine reset` (le monde ressource est régénéré au prochain redémarrage) · `/mine reset maintenant` (arrêt du serveur dans 15 s) · `/mine reset annuler` | | ✔ |
| `/event lancer\|stop\|auto\|intervalle\|joueurs` · `/godadmin teleport <s>` · `/godadmin reglages` · `/godadmin metier <joueur> <métier> <niveau>` | | ✔ |
| `/godadmin argent\|essence\|gemmes <joueur> <definir\|ajouter\|retirer> <valeur>` · `/godadmin journal [lignes] [filtre]` | | ✔ |
| `/cle donner <joueur> <commune\|rare\|epique\|divine> [n]` · `/godadmin point placer <type>` / `retirer` (déplacer caisses et pupitres) | | ✔ |
| `/godadmin clearile <joueur>` (remet un monde divin à zéro) · `/godadmin clearzone <rayon> [hauteur]` | | ✔ |

Tout joueur peut faire `/signaler <joueur> <raison>` (une fois par minute) : l'équipe connectée est prévenue
et le signalement est enregistré dans le journal. Un joueur muet ne peut pas non plus utiliser `/msg`, `/tell`, `/w` ou `/me`.
Un membre de l'équipe ne peut pas sanctionner quelqu'un de rang supérieur.

## Compiler le .jar avec GitHub (développeurs)

1. Envoyez tout le contenu de ce dossier dans le dépôt `God-mod` (racine du dépôt).
2. Le workflow `.github/workflows/build.yml` se lance à chaque push.
3. Onglet **Actions** → dernière exécution → **Artifacts** → `godmod-jar`.
4. Placez le .jar dans le dossier `mods/` du serveur (inutile chez les joueurs).

En local : `./gradlew build` (le .jar est dans `build/libs/`).

## Événements (`/event`)

Toutes les **45 minutes** (dès 2 joueurs connectés), un événement est annoncé 2 minutes à l'avance puis lancé,
avec une barre en haut de l'écran de tous les joueurs :

| Événement | Durée | Principe |
|---|---|---|
| Bénédiction des artisans | 15 min | XP de métier ×2 |
| Éclipse divine | 15 min | Essence ×2 |
| Ruée vers l'or | 15 min | Le marchand rachète tout 50 % plus cher |
| Concours de métier | 10 min | Le plus d'XP dans le métier tiré au sort : podium (clé Rare, 4 000 / 2 000 / 1 000 pièces) |
| Assaut du Titan | 10 min | Un Titan géant et ses serviteurs envahissent l'Arène (`/event rejoindre`). Récompense selon les dégâts : clé Épique au meilleur, Rare aux 2 suivants, Commune aux autres |
| Chasse au trésor | 15 min | Un coffre est caché dans le monde ressource ; la barre d'action indique distance et direction. Le premier qui l'ouvre gagne 3 000 pièces, 300 Essence, une clé Épique et 8 diamants |
| Question des dieux | ~4 min | 3 questions (calcul, mot mélangé, culture Minecraft) : le premier qui répond juste dans le chat gagne |
| Faveur divine | 5 min | Toutes les 30 s, la foudre bénie frappe des joueurs au hasard et leur offre clés ou pièces |

Admins : `/event lancer <type>` · `/event stop` · `/event auto on|off` · `/event intervalle <minutes>` ·
`/event joueurs <minimum>` · `/event liste`.

## Récompense quotidienne (`/recompense`)

Un cycle de 7 jours : 250 → 2 500 pièces, 40 → 350 Essence, clés Commune aux jours 3 et 5, clé Rare au jour 7.
Chaque semaine complète sans interruption ajoute **+10 %** (jusqu'à +100 %), et 28 jours de suite offrent une
clé Épique. Un jour manqué remet la série à 1. Rappel cliquable à la connexion, calendrier dans `/dieu`.

## Progression

Tout est visible en permanence : une **barre en haut de l'écran** (palier actuel → palier suivant,
Essence et fidèles requis) et un **tableau à droite** (argent, Foi, métier, quêtes, clés).
`/hud` l'affiche ou le masque. `/progression` ouvre le guide « Comment progresser ? ».

| Palier | Essence | Condition | Plafond de caractéristique |
|---|---|---|---|
| Mortel | 0 | — | 3 |
| Éveillé | 500 | — | 6 |
| Demi-dieu | 2 500 | peut convertir (3 fidèles max) | 10 |
| Dieu | 8 000 | 1 fidèle direct · obtient son monde divin | 15 |
| Dieu suprême | 30 000 | 8 fidèles au total | 25 |

**Essence** : métiers (0,5 par point d'XP), quêtes, montées de niveau, temps de jeu, PvP,
conversions, déicide, tournois.

### Métiers (`/metiers`)

| Métier | Gagne de l'XP en… | Bonus |
|---|---|---|
| ⛏ Mineur | cassant pierre et minerais | Célérité, double butin |
| ♣ Bûcheron | coupant des bûches | double butin, abattage d'arbre entier au niv. 20 (s'accroupir pour l'éviter) |
| ✿ Fermier | récoltant des cultures mûres | replantation automatique au niv. 5, double récolte |
| ⚔ Chasseur | tuant des créatures | double butin |
| ≈ Pêcheur | pêchant | prises doublées |

50 niveaux par métier. Chaque niveau rapporte argent + Essence ; des **clés de caisse** tous les
5 niveaux (Rare tous les 10, Épique au 25, Divine au 40 et 50). Les blocs posés par un joueur ne
rapportent pas d'XP (anti-farm).

### Quêtes (`/quetes`)

7 quêtes proposées chaque jour, **5 actives maximum**. Récompenses : argent, Essence, XP de métier,
parfois une clé. Une clé Rare bonus toutes les 10 quêtes terminées. Le pupitre du spawn ouvre aussi
le menu des quêtes.

### Caisses et clés (`/caisses`, `/cles`)

Quatre caisses sont posées au Sanctuaire : **Commune** (coffre), **Rare** (tonneau),
**Épique** (coffre de l'Ender), **Divine** (coffre-fort). Clic droit avec une clé = ouverture
avec animation de roulette ; sans clé = aperçu des gains. Clés : métiers, quêtes, ou achat
(clic droit dans `/cles`, 2 500 / 10 000 / 40 000 / 150 000 ⛃). Gains : argent, Essence, objets
rares, **générateurs de créatures**, clés, artefacts.

### Générateurs (spawners)

Obtenus dans les caisses ou dans la boutique (catégorie Divin). Posés, ils produisent leur mob ;
cassés par quelqu'un qui en a le droit, ils sont rendus intacts. Les spawners naturels des donjons
ne se récupèrent pas.

### Pouvoirs divins (`/pouvoir`, ou Codex + accroupi + clic droit)

| Palier | Pouvoirs |
|---|---|
| Éveillé | Bénédiction (soins et buffs de groupe) · Ailes célestes (bond + chute lente) |
| Demi-dieu | Foudre divine (60 blocs) · Châtiment · Miracle (pousse, nourrit, soigne) |
| Dieu | Convocation des fidèles · Aura de domination (onde de choc) · Pluie de météores |
| Dieu suprême | Jugement · Maîtrise du temps · Maîtrise de l'espace |

Chaque pouvoir coûte de la Foi et a son temps de recharge (visible dans le menu). Les pouvoirs
offensifs sont désactivés au Sanctuaire.

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

`/aide` les liste toutes, cliquables. Principales :

Joueurs : `/dieu` · `/aide` · `/recompense` · `/event [rejoindre]` · `/profil [joueur]` · `/dieu stats <carac> [points]`
· `/dieu specialisation <ocean|ciel|cosmos>` · `/pouvoir [nom] [cible]` · `/metiers` · `/quetes` · `/cles` · `/progression` · `/hud [on|off]`
· `/spawn` · `/mine` · `/monde [quitter|visiter <joueur>]` · `/warp` · `/setwarp` · `/delwarp` · `/tpa` · `/tpahere` · `/tpaccept` · `/tpdeny` · `/tpatoggle`
· `/ile [ajouter|retirer|membres|perm|pvp]` · `/shop [vendre [un|tout]|prix]` · `/argent` · `/payer <joueur> <montant>` · `/baltop`
· `/classement [puissance|argent|metiers|quetes|evenements|fideles]` · `/dieu fideles` · `/dieu liberer <joueur>` · `/dieu choix <trone|refuser>`
· `/rebellion [declarer|rejoindre]` · `/tournoi [rejoindre|quitter]` · `/trone reclamer <dieu>` · `/guerre [declarer <dieu>]`
· `/motdepasse <ancien> <nouveau>` · `/signaler <joueur> <raison>`

Les mots de passe acceptent tous les caractères (`!`, `@`, accents…), sans espace.

Admins :
`/godadmin voir <joueur>` · `essence|argent|gemmes <joueur> [definir|ajouter|retirer] <valeur>` · `palier <joueur> <palier>`
· `maitre <joueur> <maître|aucun>` · `reset <joueur> confirmer` · `arene` · `mode <serveur|petit>`
· `cooldown <heures>` · `inactivite <jours>` · `netherend <bloquer|autoriser>` · `journal [lignes]`
· `/pay <enable|disable|status>`

`/godadmin mode petit` adapte le mod à une petite survie (seuils ×0,25, Essence ×2,
rébellion toutes les 12 h, inactivité 2 jours).

Les blocs du Sanctuaire ne peuvent être cassés que par un admin en mode éditeur.

Les données sont sauvegardées dans le monde (`world/data/godmod.dat`). Le journal
(`/godadmin journal`) trace conversions, chutes, rébellions, achats premium et actions admin.
