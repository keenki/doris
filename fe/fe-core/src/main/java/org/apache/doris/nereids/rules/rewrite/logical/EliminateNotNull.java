// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.rules.rewrite.logical;

import org.apache.doris.nereids.rules.Rule;
import org.apache.doris.nereids.rules.RuleType;
import org.apache.doris.nereids.rules.rewrite.OneRewriteRuleFactory;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.IsNull;
import org.apache.doris.nereids.trees.expressions.Not;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.expressions.functions.ExpressionTrait;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.PlanUtils;
import org.apache.doris.nereids.util.TypeUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Eliminate Predicate `is not null`, like
 * - redundant `is not null` predicate like `a > 0 and a is not null` -> `a > 0`
 * - `is not null` predicate is generated by `InferFilterNotNull`
 */
public class EliminateNotNull extends OneRewriteRuleFactory {
    @Override
    public Rule build() {
        return logicalFilter()
            .when(filter -> filter.getConjuncts().stream().anyMatch(expr -> expr.isGeneratedIsNotNull))
            .then(filter -> {
                // Progress Example: `id > 0 and id is not null and name is not null(generated)`
                // predicatesNotContainIsNotNull: `id > 0`
                // predicatesNotContainIsNotNull infer nonNullable slots: `id`
                // slotsFromIsNotNull: `id`, `name`
                // remove `name` (it's generated), remove `id` (because `id > 0` already contains it)
                Set<Expression> predicatesNotContainIsNotNull = Sets.newHashSet();
                List<Slot> slotsFromIsNotNull = Lists.newArrayList();
                filter.getConjuncts().stream()
                        .filter(expr -> !expr.isGeneratedIsNotNull) // remove generated `is not null`
                        .forEach(expr -> {
                            Optional<Slot> notNullSlot = TypeUtils.isNotNull(expr);
                            if (notNullSlot.isPresent()) {
                                slotsFromIsNotNull.add(notNullSlot.get());
                            } else {
                                predicatesNotContainIsNotNull.add(expr);
                            }
                        });
                Set<Slot> inferNonNotSlots = ExpressionUtils.inferNotNullSlots(predicatesNotContainIsNotNull);

                Set<Expression> keepIsNotNull = slotsFromIsNotNull.stream()
                        .filter(ExpressionTrait::nullable)
                        .filter(slot -> !inferNonNotSlots.contains(slot))
                        .map(slot -> new Not(new IsNull(slot))).collect(Collectors.toSet());

                // merge predicatesNotContainIsNotNull and keepIsNotNull into a new ImmutableSet
                Set<Expression> newPredicates = ImmutableSet.<Expression>builder()
                        .addAll(predicatesNotContainIsNotNull)
                        .addAll(keepIsNotNull)
                        .build();
                return PlanUtils.filterOrSelf(newPredicates, filter.child());
            }).toRule(RuleType.ELIMINATE_NOT_NULL);
    }
}
