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
// This file is copied from
// https://github.com/ClickHouse/ClickHouse/blob/master/src/AggregateFunctions/AggregateFunctionBitwise.cpp
// and modified by Doris

#include "vec/aggregate_functions/aggregate_function_bit.h"

#include "vec/aggregate_functions/aggregate_function_simple_factory.h"
#include "vec/aggregate_functions/helpers.h"

namespace doris::vectorized {

template <template <typename> class Data>
AggregateFunctionPtr createAggregateFunctionBitwise(const std::string& name,
                                                    const DataTypes& argument_types,
                                                    const bool result_is_nullable) {
    if (!argument_types[0]->can_be_used_in_bit_operations()) {
        LOG(WARNING) << fmt::format("The type " + argument_types[0]->get_name() +
                                    " of argument for aggregate function " + name +
                                    " is illegal, because it cannot be used in bitwise operations");
    }

    AggregateFunctionPtr res(creator_with_integer_type::create<AggregateFunctionBitwise, Data>(
            result_is_nullable, argument_types));
    if (res) {
        return res;
    }

    LOG(WARNING) << fmt::format("Illegal type " + argument_types[0]->get_name() +
                                " of argument for aggregate function " + name);
    return nullptr;
}

void register_aggregate_function_bit(AggregateFunctionSimpleFactory& factory) {
    factory.register_function("group_bit_or",
                              createAggregateFunctionBitwise<AggregateFunctionGroupBitOrData>);
    factory.register_function("group_bit_and",
                              createAggregateFunctionBitwise<AggregateFunctionGroupBitAndData>);
    factory.register_function("group_bit_xor",
                              createAggregateFunctionBitwise<AggregateFunctionGroupBitXorData>);

    factory.register_function(
            "group_bit_or", createAggregateFunctionBitwise<AggregateFunctionGroupBitOrData>, true);
    factory.register_function("group_bit_and",
                              createAggregateFunctionBitwise<AggregateFunctionGroupBitAndData>,
                              true);
    factory.register_function("group_bit_xor",
                              createAggregateFunctionBitwise<AggregateFunctionGroupBitXorData>,
                              true);
}

} // namespace doris::vectorized