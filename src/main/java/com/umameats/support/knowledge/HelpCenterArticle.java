package com.umameats.support.knowledge;

import java.util.List;

/**
 * One help-centre entry.
 *
 * @param audience CUSTOMER, DRIVER or ALL
 * @param locale   BCP-47 language of the question and answer
 */
public record HelpCenterArticle(
        String id,
        String audience,
        String locale,
        String question,
        String answer,
        List<String> tags) {
}
