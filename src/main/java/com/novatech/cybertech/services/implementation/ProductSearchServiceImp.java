package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import com.novatech.cybertech.entities.enums.Os;
import com.novatech.cybertech.services.core.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public List<ProductDocument> search(final ProductSearchRequestDto productSearchRequestDto) {

        final String keyword = productSearchRequestDto.getKeyword();
        final Brand brand = productSearchRequestDto.getBrand().getFirst();
        final Category category = productSearchRequestDto.getCategory().getFirst();
        final Os os = productSearchRequestDto.getOs().getFirst();


        // La nouvelle façon de faire est de construire un objet "NativeQuery".
        // On utilise un "builder" qui prend des expressions lambda pour décrire la requête.
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> { // q est un "Query.Builder", l'outil pour créer notre requête.

                    // On lui dit qu'on veut une requête de type "bool" (booléenne).
                    // b est un "BoolQuery.Builder".
                    return q.bool(b -> {

                        // 1. Clause "must" pour la recherche par mot-clé (comme avant)
                        if (keyword != null && !keyword.isEmpty()) {
                            // On ajoute une condition "must" au builder "b".
                            // mm est un "MultiMatchQuery.Builder".
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("name", "description") // Les champs où chercher
                                    )
                            );
                        }

                        // 2. Clause "filter" pour les filtres exacts (comme avant)
                        if (brand != null) {
                            // On ajoute une condition "filter" au builder "b".
                            // t est un "TermQuery.Builder".
                            b.filter(f -> f
                                    .term(t -> t
                                            .field("brand") // Le champ à filtrer
                                            .value(brand.name()) // La valeur exacte
                                    )
                            );
                        }
                        if (category != null) {
                            b.filter(f -> f
                                    .term(t -> t
                                            .field("category")
                                            .value(category.name())
                                    )
                            );
                        }
                        if (os != null) {
                            b.filter(f -> f
                                    .term(t -> t
                                            .field("os")
                                            .value(os.name())
                                    )
                            );
                        }

                        return b; // On retourne le builder "bool" configuré.
                    });
                })
                .build();

        // Cette partie ne change pas !
        var hits = elasticsearchOperations.search(searchQuery, ProductDocument.class);

        return hits.stream()
                .map(SearchHit::getContent)
                .toList();
    }

}