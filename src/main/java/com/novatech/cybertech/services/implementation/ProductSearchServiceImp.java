package com.novatech.cybertech.services.implementation;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.novatech.cybertech.dto.request.search.ProductSearchRequestDto;
import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.services.core.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchServiceImp implements ProductSearchService {

    private static final String DOT = ".";
    private static final String BRAND = "brand";
    private static final String PRICE = "price";
    public static final String NAME_FIELD = "name";
    private static final String CATEGORY = "category";
    private static final String ATTRIBUTES = "attributes";
    public static final String DESCRIPTION_FIELD = "description";

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public List<ProductDocument> search(final ProductSearchRequestDto productSearchRequestDto) {

        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 1) Filtre catégorie (exact)
        if (productSearchRequestDto.getCategory() != null) {
            bool.filter(f -> f.term(t -> t.field(CATEGORY).value(productSearchRequestDto.getCategory().name())));
        }

        // 2) Filtre brands (multi-sélection via terms)
        if (productSearchRequestDto.getBrands() != null && !productSearchRequestDto.getBrands().isEmpty()) {
            List<FieldValue> brandValues = productSearchRequestDto.getBrands().stream()
                    .map(b -> FieldValue.of(b.name()))
                    .toList();
            bool.filter(f -> f.terms(t -> t.field(BRAND).terms(v -> v.value(brandValues))));
        }

        // 3) Recherche Textuelle (MultiMatch)
        if (StringUtils.hasText(productSearchRequestDto.getKeyword())) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(productSearchRequestDto.getKeyword())
                    .fields(NAME_FIELD, DESCRIPTION_FIELD)
                    .fuzziness("AUTO") // Optionnel : permet les fautes de frappe
            ));
        }

        // --- AJOUT 4) Filtrage par Range de Prix ---
        // --- Filtrage par Range de Prix ---
        if (productSearchRequestDto.getPriceMin() != null && productSearchRequestDto.getPriceMax() != null) {
            bool.filter(f -> f.range(r -> r
                    .number(n -> n
                            .field("price")
                            .gte(productSearchRequestDto.getPriceMin())
                            .lte(productSearchRequestDto.getPriceMax())
                    )
            ));
        }


        // --- AJOUT 5) Filtrage sur les attributs dynamiques (Flattened) ---
        // --- Filtrage sur les attributs dynamiques (Flattened) ---
        if (productSearchRequestDto.getAttributes() != null) {
            productSearchRequestDto.getAttributes().forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    // On transforme la liste de String en liste de FieldValue
                    List<FieldValue> attrValues = values.stream()
                            .map(FieldValue::of)
                            .toList();

                    bool.filter(f -> f.terms(t -> t
                            .field(ATTRIBUTES + DOT + key)
                            .terms(v -> v.value(attrValues))
                    ));
                }
            });
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class)
                .map(SearchHit::getContent)
                .toList();
    }
}