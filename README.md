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

- `/spawn` : Sanctuaire · `/mine` : monde ressource, arrivée **en parachute** haut dans le ciel à un endroit aléatoire
  (chute lente, aucun dégât de chute jusqu'au sol, accroupi pour replier le parachute) · `/monde` : votre monde divin.
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

## Anti-bot (`/antibot`)

Ne concerne que les **nouveaux comptes jamais inscrits** (`/register`) : les joueurs déjà inscrits ne sont jamais touchés.

- **Pseudos générés** du type `QuietWhale5404` (Adjectif + Nom + 4 chiffres) : refusés.
- **Datacenters, VPN et proxys** : l'adresse est vérifiée en arrière-plan auprès du service gratuit ip-api.com
  (l'adresse IP du nouveau compte lui est envoyée) ; si c'est un hébergeur, le compte est expulsé.
- **Vague de bots** : 4 nouveaux comptes en une minute → inscriptions fermées 10 minutes et non-inscrits expulsés.
- Un compte qui ne fait pas `/register` est de toute façon expulsé au bout de 90 secondes (si `/auth on`).
- L'équipe connectée est prévenue de chaque refus (et tout est noté dans le journal).

Admins : `/antibot` (état) · `/antibot noms|ip|vague on|off` · `/antibot autoriser <pseudo>` (laisser entrer
un vrai joueur au pseudo généré, ex. Bedrock) · `/antibot scan` (appliquer aux joueurs connectés) ·
`/antibot expulser` (tous les non-inscrits) · `/antibot ouvrir` (rouvrir les inscriptions) ·
`/antibot purge` (supprimer les profils fantômes) · `/antibot bannirip <joueur|ip>` · `/antibot debannirip <ip>`.

## Trailer (`/trailer_lancer [adresse]`, admins)

Une cinématique d'environ 2 min 20 pour filmer un trailer : la caméra de l'admin enchaîne 11 plans
(orbite aérienne du Sanctuaire, travelling sur l'avenue, grue sur le temple, caisses, métiers et quêtes,
boutique, Arène avec foudre divine, monde divin de l'admin s'il en a un, descente en parachute dans le monde
ressource, feu d'artifice, final face au temple avec l'adresse du serveur). Un **mannequin portant le skin de
l'admin** joue son personnage. Musique : disque « otherside ».

Pendant le tournage : le chat est en pause pour tout le monde, l'admin ne reçoit aucun message ni HUD
(appuyez sur **F1** pour masquer le reste de l'interface et enregistrez l'écran). `/trailer_stop` pour arrêter.
À la fin, l'admin retrouve sa position et son mode de jeu.

## Zones (`/zone`, admins)

`/zone baton` donne un **bâton de zone** : clic gauche sur un bloc = coin 1, clic droit = coin 2 (la sélection
s'affiche en particules). Puis :

- `/zone creer afk <nom>` : la zone compte comme zone AFK (jetons, pas d'expulsion pour inactivité), dans n'importe quel monde ;
- `/zone creer spawn <nom>` : zone protégée comme le Sanctuaire (pas de casse, pose, coffres, PvP, monstres), n'importe où ;
- `/zone creer libre <nom>` : retire la protection d'une partie du Sanctuaire ;
- `/zone creer commande <nom> <console|joueur> <commande>` : lance la commande chaque fois qu'un joueur **entre** dans la zone.
  `{joueur}` est remplacé par son pseudo. `console` = avec tous les droits (ex. `/zone creer commande bonus console give {joueur} diamond 1`),
  `joueur` = comme si le joueur la tapait (ex. `/zone creer commande portail joueur mine`) ;
- ajouter `toutehauteur` à la fin étend la zone du bas au haut du monde ;
- `/zone supprimer` (la zone où vous êtes) ou `/zone supprimer <nom>` · `/zone liste` · `/zone pos1|pos2` (coin à vos pieds).

Bâton en main, les zones proches sont dessinées en particules (violet = AFK, vert = protégée, flammes = libre, symboles d'enchantement = commande).

## Tableaux du tutoriel (`/tuto`, admins)

Regardez le **centre** du mur où mettre le panneau, puis `/tuto tableau <n>` (1 à 14) : la page s'affiche
plaquée sur le mur sur **exactement 5 × 4 blocs** : panneau de fond toujours éclairé (même la nuit), cadre doré,
et texte agrandi automatiquement pour remplir le panneau. `/tuto fond <bloc>` et `/tuto cadre <bloc|aucun>` changent
le fond et le cadre. `/tuto tableau <n> ici` le pose
3 blocs devant vous, tourné vers vous. Reposer un numéro remplace l'ancien. `/tuto` liste les pages (cliquables),
`/tuto retirer <n>` ou `/tuto retirer tous`.
Les positions sont enregistrées : `/tuto actualiser` redessine tous les tableaux posés (après une mise à jour des textes),
et `/tuto` indique ✔/✖ ceux qui sont posés. Après chaque pose, un bouton propose le tableau suivant.

Pages : 1 Bienvenue · 2 Essence et paliers · 3 Métiers · 4 Quêtes · 5 Caisses et clés · 6 Monde ressource ·
7 Pouvoirs et Foi · 8 Fidèles et conflits · 9 Monde divin · 10 Économie · 11 Marché et échanges · 12 Confort ·
13 Événements et cadeaux · 14 À vous de jouer.

## Objets pour l'équipe (`/donner`, admins)

`/donner <objet> [nombre] [joueur]` : `cle_commune|rare|epique|divine`, `artefact_ocean|ciel|cosmos`, `codex`,
`jetons_afk`, `generateur_<créature>` (Tab pour la liste). Sans joueur, c'est pour soi.

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

## Générateur de l'île (`/generateur`)

Dans un monde divin, un générateur lave + eau ne produit plus seulement de la pierre : il fait apparaître des
blocs tirés au sort selon le **prestige du générateur** du propriétaire de l'île (I à XX).

| Prestige | Nouveaux blocs |
|---|---|
| II | pierre, charbon |
| III – V | cuivre, fer, andésite, diorite, granite |
| VI – IX | lapis, redstone, or, ardoise des abîmes |
| X – XIV | diamant, émeraude, améthyste |
| XV – XX | obsidienne, diamant des abîmes, débris antiques, quartz |

Chaque prestige augmente aussi la part des minerais déjà débloqués. Prix : de 2 000 pièces (II) à environ
440 000 (XX), et il faut un niveau de Mineur (2 × le prestige visé − 2). Les minerais du générateur donnent de
l'XP de Mineur. Le menu montre la production actuelle (en %) et celle du prestige suivant.

## Anti-lag

- **15 créatures d'un même type par chunk au plus** (villageois, golems, animaux apprivoisés ou nommés, Titan exceptés) :
  au-delà, les nouvelles n'apparaissent plus (spawners, élevage, apparitions naturelles).
- **Nettoyage des objets au sol toutes les 90 secondes** (les objets décoratifs sans gravité, `NoGravity:1b`, sont épargnés). Le compte à rebours est affiché en permanence dans le
  tableau à droite (❄ Nettoyage : 1:12), avec des alertes à 30 s, 10 s et 5 s.
- Admins : `/clearlag` (tout de suite) · `/clearlag intervalle <secondes>` (0 = désactivé) · `/clearlag entites <max>` (0 = sans limite).

## Statistiques (`/stats`)

`/stats [joueur]` ouvre un panneau complet : combat (victoires, morts, ratio, monstres), récolte (blocs minés,
minerais, diamants, bûches, cultures, poissons, blocs posés), artisanat et commerce (objets fabriqués, fondus,
animaux nés, échanges, ventes, pièces gagnées), progression, assiduité, richesse et métiers, avec le rang du
joueur dans chaque classement. `/stats serveur` : totaux du serveur et records (top 3 de chaque catégorie).

## Zone AFK (`/afk`, `/afkshop`)

`/afk` envoie dans la zone AFK du Sanctuaire (construite automatiquement à l'écart, en x = -220) :
**1 jeton AFK et 5 pièces par minute**, 8 h par jour au plus. Un joueur dans la zone n'est jamais expulsé pour
inactivité ; il reste donc connecté et compte comme joueur en ligne. Un joueur immobile 5 minutes est marqué
`[AFK]` dans la liste des joueurs. `/afk` ou `/spawn` pour sortir.

`/afkshop` : pièces, Essence, clés (Commune 60, Rare 240, Épique 900 jetons), boost des métiers ×1,5 pendant 1 h,
fioles d'XP, pomme dorée enchantée, générateurs, et tous les privilèges.

## Privilèges (`/shop` → Privilèges, ou `/privileges`)

Des commandes de confort achetées une fois pour toutes, volontairement chères (ou en jetons AFK) :

| Commande | Effet | Prix | Jetons AFK |
|---|---|---|---|
| `/chapeau` | porter l'objet en main sur la tête | 15 000 | 400 |
| `/craft` | établi portable | 25 000 | 600 |
| `/manger` | se rassasier (toutes les 10 min) | 30 000 | 700 |
| `/ec` | coffre de l'Ender portable | 40 000 | 900 |
| `/enclume` | enclume portable | 50 000 | 1 200 |
| `/tete [joueur]` | tête d'un joueur (toutes les heures) | 60 000 | 1 500 |
| `/soin` | soin complet (toutes les 30 min, hors combat) | 80 000 | 2 000 |
| `/reparer` | répare l'objet en main (toutes les 2 h) | 120 000 | 3 000 |
| `/vol` | voler dans son propre monde divin | 200 000 | 5 000 |

Les admins ont tous les privilèges. `/skin` n'est pas possible avec un mod côté serveur (il faut un mod dédié).

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
| ⚡ Titan | 80 000 | transcendance : 8 fidèles, 100 niv. de métier, générateur V, offrande 100 000 | 30 |
| ✺ Primordial | 200 000 | 9 fidèles, 140 niv., générateur VIII, offrande 250 000 | 35 |
| ✧ Divinité astrale | 450 000 | 10 fidèles, 180 niv., générateur XII, offrande 600 000 | 40 |
| ∞ Éternel | 1 000 000 | 11 fidèles, 220 niv., générateur XVI, offrande 1 500 000 | 45 |
| ☀ Créateur | 2 500 000 | 12 fidèles, 250 niv. (tous les métiers au max), générateur XX, offrande 4 000 000 | 50 |

**Transcendance** (`/transcender`) : au-delà du Dieu suprême, on ne monte plus tout seul. Quand toutes les
conditions sont réunies, le joueur est prévenu et fait l'offrande (les pièces sont dépensées). Chaque palier
transcendant donne +5 % d'XP de métier et d'Essence, des caractéristiques plus hautes, plus de Foi, un monde
divin plus grand et plus de warps (jusqu'à 20). Les fidèles sont comptés au total à partir du Dieu suprême.

**Essence** : métiers (0,5 par point d'XP), quêtes, montées de niveau, temps de jeu, PvP,
conversions, déicide, tournois.

### Métiers (`/metiers`, `/metiers <métier>`)

50 niveaux par métier. Chaque niveau rapporte argent + Essence ; des **clés** tous les 5 niveaux (Rare tous
les 10, Épique au 25 et 40, Divine au 50). Les blocs posés par un joueur ne rapportent rien (anti-farm).
Chaque jour, un **métier en vogue** gagne 50 % d'XP en plus. Cliquer sur un métier montre ses talents,
son barème d'XP et le classement des 5 meilleurs. Au niveau 50, l'XP devient de l'Essence ×1,5.

| Métier | XP | Talents (niveau) |
|---|---|---|
| ⛏ Mineur | pierre, minerais, lingots sortis du four | minerai doublé (1) · vision nocturne en profondeur (10) · Célérité I (15) · **filon** : les minerais reliés partent ensemble (25) · Célérité II (35) · fortune divine (50) |
| ♣ Bûcheron | bûches | bûche doublée (1) · Vitesse I (10) · **abattage** de l'arbre entier (20) · pommes et pousses (30) · abattage 160 bûches + Force I (40) |
| ✿ Fermier | cultures mûres, élevage | récolte doublée (1) · replantation (5) · faim qui remonte (10) · **engrais divin** autour de soi (20) · **moisson 3×3** à la houe (30) · carottes dorées (40) |
| ⚔ Chasseur | créatures | prime en pièces (1) · Régénération (10) · Force après chaque monstre (20) · clés trophées (30) · **générateur du monstre tué** (40) · prime doublée (50) |
| ≈ Pêcheur | prises | prise doublée (1) · clés à l'hameçon (10) · effet Chance (20) · trésors engloutis (30) · respiration + grâce du dauphin (40) · clés Rares (50) |

S'accroupir désactive l'abattage, le filon et la moisson large. Ces talents respectent les permissions des îles.

### Quêtes (`/quetes`)

- **Quêtes du jour** : 7 propositions chaque jour (2 ★ faciles, 3 ★★ normales, 1 ★★★ difficile, 1 ★★★★ épique),
  **5 actives au maximum**. Les gains montent avec la difficulté (de 300 à 3 000 pièces, clé Commune ou Rare pour les
  plus dures) et avec votre palier (+20 % par palier). Objectifs variés : minage, coupe, récolte, élevage, chasse, pêche,
  artisanat, fonte et cuisson, échanges avec les villageois, ventes au marchand, caisses, PvP, temps de jeu.
- `/quetes renouveler` : nouvelles propositions (gratuit 1 fois par jour, puis payant).
- **Bonus d'assiduité** : 3 quêtes dans la journée → clé Commune + 500 pièces ; 5 quêtes → clé Rare + 1 500 pièces.
  Une clé Rare toutes les 10 quêtes.
- **Défis de la semaine** (`/quetes semaine`) : 3 grands objectifs actifs d'office, renouvelés le lundi
  (6 000 pièces, 1 800 Essence et une clé Rare chacun ; clé Épique si les 3 sont relevés).
- **Quêtes d'ascension** (`/ascension`) : un fil rouge de 21 étapes, de « Premiers pas » à « Créateur », qui guide
  de mortel à dieu suprême et se valide tout seul.
- La quête la plus avancée et l'étape d'ascension s'affichent dans le tableau à droite ; `/quetes liste` résume tout dans le chat.

### Caisses et clés (`/caisses`, `/cles`)

Quatre caisses : **Commune**, **Rare**, **Épique**, **Divine** (points posés au Sanctuaire ou par `/godadmin point placer`).
Clic droit = aperçu ; clés : métiers, quêtes, votes, événements, récompense du jour, `/afkshop`, ou achat dans `/cles`
(2 500 / 10 000 / 40 000 / 150 000 ⛃). Gains : argent, Essence, objets rares, **générateurs de créatures**, clés, artefacts.
- **Aperçu** : cadre aux couleurs de la caisse, récompenses de la plus fréquente à la plus rare avec leur rareté
  (Commun ≥ 12 %, Peu commun ≥ 6 %, Rare ≥ 3 %, Épique ≥ 1,5 %, Légendaire), la chance exacte et une jauge.
- **Ouverture** : roulette au cadre animé, ralentissement, révélation dorée de la récompense au centre.
  **Ouverture rapide ×5** (bouton, ou `/caisses ouvrir <caisse> <nombre>`) : sans animation, gains résumés dans le chat.
- **Coffres 3D** (admins, à lancer une fois : `/caisses decor`) : au-dessus de chaque caisse, le bloc existant sert de
  socle (**aucun bloc n'est modifié**) et un vrai coffre modélisé dans le pack de ressources apparaît — corps,
  couvercle articulé, joyau flottant, un style par rareté (bois et fer, bleu et argent, obsidienne et améthyste,
  marbre et or avec couronne). Au repos le couvercle s'entrouvre, le joyau tourne et brille, un anneau de particules
  l'entoure ; quand un joueur ouvre une caisse, le couvercle s'ouvre grand, le joyau s'élève avec une colonne de
  lumière, puis tout se referme. Clic droit sur le coffre = aperçu de la caisse. `/caisses decor hauteur <blocs>`
  (le monter ou le descendre), `/caisses decor retirer`, `/caisses decor anciens` (supprime les anciens panneaux
  « Caisse … / clic droit »). Modèles et textures : `pack/crates.py`.

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

## Pack de ressources (`pack/`, `/pack`)

Le serveur envoie à chaque connexion un pack de ressources (≈ 21 Ko), **obligatoire par défaut**. Il est dessiné par
`pack/build_pack.py` (aucune texture de Mojang) et publié avec le site : **https://loup0007a.github.io/God-mod/pack.zip**.
- **Badges en images dans le texte** : `ADMIN`, `MODO`, `AFK`, les 10 paliers, les canaux du chat (G, L, C, P, ✉),
  le préfixe `PANTHÉON` des messages du serveur, et des icônes (pièces, Essence, Foi, clés) dans le tableau de droite.
- **Interface** : coffres et menus en marbre et or (toutes les interfaces de coffre), boutons, barre d'objets
  et barres de boss redessinés.
- Le serveur télécharge le pack au démarrage puis toutes les 30 minutes pour connaître son empreinte : une
  nouvelle version publiée est proposée aux joueurs à leur connexion suivante (ou tout de suite avec `/pack recharger`).
- `/pack` : le renvoyer. Admins : `/pack etat` · `/pack recharger` · `/pack activer on|off` ·
  `/pack obligatoire on|off` (facultatif = les badges redeviennent du texte, puisqu'un joueur sans pack verrait des
  carrés) · `/pack url <adresse>`.
- Limites : un pack ne peut ajouter ni fenêtre ni bouton (les onglets du chat restent les liens cliquables). Pour
  une vraie interface façon NationsGlory (onglets dans le chat, HUD sur mesure), il faudrait un mod à installer
  chez chaque joueur.

## Mod client facultatif (`godmod.jar` chez le joueur)

Le même `.jar` s'installe aussi chez les joueurs (Forge 1.21.11) ; sur un client, seule l'interface est active
(le jeu du mod ne tourne que sur le serveur dédié). Il remplace le chat par un **panneau à vrais onglets** :
Tout, Global, Local, Commerce, Groupe, Privé (compteurs de non-lus), canal d'écriture cliquable (`✎ Global`,
clic droit = canal précédent), défilement à la molette, info-bulles et clics des messages. Chat fermé : les derniers
messages de l'onglet actif s'estompent comme d'habitude. Les messages sont classés grâce au badge de canal.
Le mod se signale (`/godclient`) uniquement sur God Civilisation.
- Téléchargement (mis à jour à chaque compilation) : https://github.com/Loup0007A/God-mod/releases/download/mod/godmod.jar ;
  instructions sur le site, section « Mod client ».
- Le serveur propose l'installation une fois par jour aux joueurs qui ne l'ont pas. Admins : `/client` (qui l'a) ·
  `/client demande on|off` · `/client obligatoire on|off` (sans le mod : expulsion après 45 s, avec le lien) ·
  `/client url <adresse>`.
- Les joueurs sans le mod gardent le chat du serveur (onglets simulés avec `/chat`).

## Chat (`/chat`, `/canal`, `/mp`, `/groupe`)

Le chat vanilla est remplacé par celui du mod (les messages restent dans le chat normal du jeu).
- **Mise en forme** : `[G]` canal (survol : heure ; clic : écrire dans ce canal) · rang et palier · pseudo avec sa
  fiche au survol (palier, Essence, dieu, fidèles, monde, AFK) et clic = message privé · « » » · message.
  `[item]` montre l'objet en main (description complète au survol) ; un pseudo connecté (ou `@pseudo`) est
  mis en valeur et le joueur mentionné entend une notification ; liens cliquables ; couleurs `&a`… pour les admins.
  Anti-répétition (1 message par seconde, pas deux fois le même en 6 s ; l'équipe n'est pas limitée).
- **Canaux** : Global, Local (100 blocs, même monde), Commerce, Groupe. `/canal [canal]` choisit où l'on écrit
  (menu cliquable sans argument) ; `/g`, `/local`, `/commerce`, `/p <message>` pour un message ponctuel ;
  `!message` = Global depuis n'importe quel canal.
- **Onglets** : un serveur ne peut pas ajouter de vrais onglets au chat du jeu ; ils sont simulés. `/chat` affiche la
  barre `[Tout] [Global (3)] [Local] [Commerce] [Groupe] [Privé]` : un clic efface l'écran, réaffiche les 60 derniers
  messages du canal et n'affiche plus que lui (messages du serveur compris) ; les autres canaux sont comptés et un
  message privé ou une mention s'annonce dans la barre d'action. `/chat masquer <canal>` cache un canal de l'onglet
  Tout. `/chat effacer`. L'historique est gardé en mémoire (120 lignes par joueur) ; les messages purement vanilla
  (morts, progrès) ne sont pas réaffichés. Dans l'onglet Privé, écrire normalement répond au dernier correspondant.
- **Messages privés** : `/mp <joueur> <message>`, `/r <message>`, `/mp off|on`, `/ignorer [joueur]`
  (l'équipe ne peut pas être ignorée). Un joueur muet ne peut rien envoyer. Équipe : `/espion` (voir les MP).
  Les messages (canaux et MP) sont écrits dans la console du serveur.
- **Groupes** : `/groupe creer|inviter <joueur>|accepter|refuser|quitter|expulser <joueur>|chef <joueur>`, 8 joueurs.
  `/groupe` liste les membres avec leur monde, leurs coordonnées et leur vie. Le groupe se défait au redémarrage.

## Courrier (`/courrier`)

Écrire à n'importe quel joueur, même hors ligne : `/courrier envoyer <joueur> <message>`, joindre l'objet tenu en main
(`/courrier objet <joueur> [message]`) ou des pièces (`/courrier argent <joueur> <montant> [message]`).
`/courrier` ouvre la boîte aux lettres (30 lettres au plus) : clic = lire et récupérer les pièces jointes, clic droit =
répondre, shift-clic = supprimer. Notification à la réception et à la connexion. Un joueur muet ne peut pas écrire ;
un joueur qui vous ignore ne reçoit pas vos lettres.

## Primes (`/prime`)

`/prime <joueur> <montant>` met de l'argent sur la tête d'un joueur (500 pièces au moins, taxe de 10 %, plusieurs joueurs
peuvent cumuler). Le joueur recherché porte un **☠** rouge devant son nom (montant au survol). Celui qui le tue en combat
touche la prime (sa propre participation lui est simplement rendue) ; annonce dans le chat. Sans preneur, la prime expire
après 7 jours sans nouvelle mise et les participants sont remboursés. `/prime` : les 10 plus grosses primes.

## Annonces automatiques (`/annonces`, admins)

Toutes les 10 minutes (dès qu'un joueur est en ligne), une annonce de la liste est diffusée à tour de rôle ; les commandes
qu'elle cite (/vote, /kit…) sont cliquables. 7 annonces par défaut (vote, marché, kits, site, primes, courrier, aide).
`/annonces` (liste, avec ✖ pour retirer) · `ajouter <texte>` · `retirer <n>` · `intervalle <minutes>` · `on|off` · `maintenant`.

## Infos (`/vu`, `/ping`, `/tps`)

`/vu <joueur>` : en ligne ou dernière connexion, date d'arrivée, palier, prime. `/ping [joueur]` : latence.
`/tps` : TPS, millisecondes par tick, joueurs, mémoire.

## Votes (`/vote`)

`/vote` affiche les trois sites (liens cliquables) et le temps avant de pouvoir revoter sur chacun.
Après avoir voté, `/vote recuperer` vérifie le vote auprès de chaque site (par l'adresse IP du joueur) et donne
**750 pièces, 100 Essence et une clé Commune** par vote, plus une **clé Rare** si les trois sites sont votés le même
jour. Chaque vote est annoncé dans le chat. `/vote top` : meilleurs voteurs du mois. Rappel à la connexion.

| Site | Délai | Vérification |
|---|---|---|
| liste-serveurs-minecraft.org | 3 h | prête (identifiant 209022) |
| top-serveurs.net | 2 h | `/vote config top-serveurs <jeton>` (jeton du serveur, panel du site → API) |
| serveur-prive.net | 1 h 30 | `/vote config serveur-prive <clé>` (clé d'API, panel du site → API) |

Admins : `/vote config` (état) · `/vote config <site> <clé|aucun>` · `/vote recompense <pièces> <essence>`.

## Site web (`site/`)

Le site du serveur (accueil, statut en direct, wiki, toutes les commandes, page de vote) est dans `site/page.html`.
Le workflow `.github/workflows/site.yml` le construit (`python3 site/build.py`, qui relit aussi les commandes de
`/aide` dans le code) et le publie sur GitHub Pages à chaque modification :
**https://loup0007a.github.io/God-mod/**. À faire une seule fois : dépôt → *Settings* → *Pages* → *Source* :
**GitHub Actions**. Le statut et la liste des joueurs viennent de l'API publique mcsrvstat.us.
En jeu : `/site` (ou `/wiki`) donne le lien ; `/site definir <adresse>` si vous le mettez ailleurs.

## Résidences et retour (`/sethome`, `/home`, `/homes`, `/delhome`, `/back`)

- `/sethome [nom]` pose une résidence (nom par défaut : « maison »). Nombre : 1 + 1 par palier (Mortel 1 … Créateur 10),
  +5 pour l'équipe. Interdit sur l'île d'un dieu qui ne vous laisse pas entrer.
- `/home [nom]`, `/homes` (liste cliquable), `/delhome <nom>`. Délai de téléportation et blocage en combat habituels.
- `/back` (ou `/retour`) : revient au lieu de votre mort, ou à l'endroit quitté lors de la dernière téléportation.
- Si l'endroit est bouché (monde ressource réinitialisé…), on arrive au-dessus, avec 3 s d'invulnérabilité.

## Kits (`/kit`)

Menu cliquable, ou `/kit <nom>` : **depart** (une fois), **quotidien** (toutes les 24 h, plus riche à chaque palier),
**eveille** (3 jours, outils en fer), **demidieu** (7 j, armure de fer), **dieu** (7 j, outils en diamant),
**supreme** (7 j, armure de diamant), **titan** (7 j, netherite, totem…). Un rappel s'affiche à la connexion.

## Échange sécurisé (`/echange <joueur>`)

L'autre joueur accepte (`/echange accepter`). Chacun clique ses objets (16 piles au plus) et règle l'argent donné
(boutons ±100 / ±1 000, clic droit ×10). Quand les deux ont validé, l'échange a lieu 3 s plus tard ; toute modification
annule les validations. Fermer l'écran, se déconnecter ou un redémarrage annule et rend tout.

## Tombes (`/tombe`)

À la mort, les objets ne tombent plus au sol : ils vont dans une tombe (crâne flottant + nom) à l'endroit du décès.
Le propriétaire récupère tout en marchant dessus ; le tueur (JcJ) peut la piller tout de suite, tout le monde après
10 minutes ; elle disparaît au bout de 2 heures. `/tombe` liste vos tombes et permet de les récupérer à distance
(500 pièces, gratuit après une chute dans le vide). Admins : `/tombe activer on|off`, `/tombe prix <pièces>`.

## Marché des joueurs (`/market`, `/marche`, `/hdv`)

Les joueurs vendent leurs propres objets au prix qu'ils choisissent.
- **Vendre** : tenez l'objet, `/market vendre <prix à l'unité> [quantité]` (toute la pile par défaut). Le bouton
  émeraude du menu propose un prix conseillé (prix moyen, sinon boutique). 15 offres par joueur, 7 jours chacune.
- **Taxe** : 1 pièce par objet vendu, retenue sur le gain du vendeur (prix minimum : 2 pièces).
- **Acheter** : clic gauche = tout le lot, clic droit = 1 exemplaire (ou `/market acheter <offre> [quantité]`).
- **Trier** (entonnoir, ou `/market tri`) : récentes, prix croissant/décroissant, meilleures affaires (prix le plus
  bas par rapport au prix moyen), quantité, nom.
- **Rechercher** (panneau, ou `/market recherche <texte>`) : nom ou identifiant anglais (`diamond`, `iron`), pseudo
  du vendeur, ou `main` pour l'objet tenu.
- **Chaque offre affiche** le prix moyen des ventes sur 7 jours, l'écart avec ce prix, le prix de la boutique
  (achat/revente) et les courbes sur 14 jours de l'offre ▁▃▅█, de la demande et du prix.
- **Graphique** (shift-clic sur une offre, longue-vue du menu, ou `/market courbe [objet]`) : barres sur 9 jours
  pour l'offre, la demande ou le prix moyen, avec les chiffres au survol et une tendance.
- `/market prix` : statistiques de l'objet tenu · `/market mesventes` : vos offres (clic pour retirer) ·
  `/market recuperer` : objets d'offres expirées. Les ventes faites hors ligne sont résumées à la connexion.
- Interdits : objets divins, générateurs, baguette de zone. Les modérateurs peuvent retirer une offre
  (`/market retirer <offre>`) : les objets reviennent au vendeur.

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
