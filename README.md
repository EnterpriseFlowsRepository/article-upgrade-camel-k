# Exemples de code
## De l'article [Upgrade Camel-K 1.10 en 2.1](https://www.middleware-solutions.fr/?p=1542)

## Justifications

### Processor [[JsonToXml.java]]

Depuis que `camel-quarkus` ne porte plus le projet AtlasMap, il n'est plus possible d'utiliser les transformations JSON vers XML proposées par ce dernier.
De façon générale, il faut remplacer de simple processus en une transormation double :
1) Une transformation du JSON dans un premier temps. Avec JSONata, il est facile de transformer une structure en une autre.
2) Puis il convient de transformer le JSON en XML. Il s'agit de deux représentations textuelles basiques de la donnée.

Si la première étape se fait aisément, c'est moins le cas de la seconde. Jackson est une solution possible, JSON.org en est une autre. Cependant, ces deux solutions ont la facheuse tendance de mal gérer les tableaux anonymes. Voici un exemple.

Mettons qu'en entrée nous ayons le JSOn suivant:
```json
{
    "elements": [
        {
            "value": "foo"
        },
        {
            "value": "bar"
        }
    ]
}
```

Maintenant, voici ce qu'une transformation Jackson ou JSON.org va créer :
```xml
<elements>
    <value>foo</value>
</elements>
<elements>
    <value>bar</value>
</elements>
```

Ici, `<elements>` était la racine de notre JSON. Or, le XML obtenu possède plusieurs racines et est donc invalide. Je n'ai pas envie de faire une troisière transformation pour rendre ce payload valide. Le fichier XmlToJson.java propose une transformation qui, avec comme entrée le même json, va générer:

```xml
<elements>
    <element>
        <value>foo</value>
    </element>
    <element>
        <value>bar</value>
    </element>
</elements>
```

Le tout sans configuration supplémentaire. En effet, le tableau se nomme "elements" avec un "s". Le script va simplement retirer le 's' du nom du parent pour nommer les nœuds XML enfants.

Quelques argument permettent tout de même de configurer un peu plus ce processor, notamment en permettant de surcharger le nom des enfants des tableaux ou des objets. Tout est détaillé dans les commentaires du fichier.

### RouteBuilder

TODO

## Notes

Tous les extraits de code présentés dans ce repository sont librement utilisables.
Auteur original: [Timothé Rosaz](https://www.linkedin.com/in/timothe-rosaz/).
