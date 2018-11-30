// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package fulcro

import (
	"github.com/russolsen/transit"
	"gopkg.in/resty.v1"
)

func Keywordize(data interface{}) interface{} {
	var result interface{}
	switch v := data.(type) {
	case []map[string]interface{}:
		newArray := make([]map[transit.Keyword]interface{}, len(v))
		for i, m := range v {
			newArray[i] = Keywordize(m).(map[transit.Keyword]interface{})
		}
		result = newArray
	case map[string]interface{}:
		newMap := make(map[transit.Keyword]interface{})
		for k, val := range v {
			newMap[transit.Keyword(k)] = Keywordize(val)
		}
		result = newMap
	case []interface{}:
		for i, val := range v {
			v[i] = Keywordize(val)
		}
		result = v
	default:
		result = v
	}
	return result
}

func MakeKeyword(data interface{}, keys ...string) interface{} {
	var result interface{}
	switch v := data.(type) {
	case []map[string]interface{}:
		newArray := make([]map[string]interface{}, len(v))
		for i, m := range v {
			newArray[i] = MakeKeyword(m, keys...).(map[string]interface{})
		}
		result = newArray
	case map[string]interface{}:
		key := keys[0]
		val, ok := v[key]
		if ok {
			if len(keys) == 1 {
				v[key] = transit.Keyword(val.(string))
			} else {
				v[key] = MakeKeyword(val, keys[1:]...)
			}
		}
		result = v
	default:
		result = v
	}
	return result
}

func Remove(data interface{}, keys ...string) interface{} {
	var result interface{}
	switch v := data.(type) {
	case []map[string]interface{}:
		for i, m := range v {
			v[i] = Remove(m, keys...).(map[string]interface{})
		}
		result = v
	case map[string]interface{}:
		key := keys[0]
		val, ok := v[key]
		if ok {
			if len(keys) == 1 {
				delete(v, key)
			} else {
				v[key] = Remove(val, keys[1:]...)
			}
		}
		result = v
	default:
		result = v
	}
	return result
}

func AddType(data interface{}, t string) interface{} {
	result := data.([]map[string]interface{})
	for i, _ := range result {
		result[i]["type"] = transit.Keyword(t)
	}
	return result
}

func GetString(args map[interface{}]interface{}, id string) string {
	result := ""
	val := args[transit.Keyword(id)]
	if val != nil {
		result = val.(string)
	}
	return result
}

func GetKeyword(args map[interface{}]interface{}, id string) transit.Keyword {
	return args[transit.Keyword(id)].(transit.Keyword)
}

func GetKeywordAsString(args map[interface{}]interface{}, id string) string {
	return string(GetKeyword(args, id))
}

func GetInt(args map[interface{}]interface{}, id string) int64 {
	return args[transit.Keyword(id)].(int64)
}

func GetBool(args map[interface{}]interface{}, id string) bool {
	return args[transit.Keyword(id)].(bool)
}

func GetTempId(args map[interface{}]interface{}, id string) transit.TaggedValue {
	return args[transit.Keyword(id)].(transit.TaggedValue)
}

func GetMap(args map[interface{}]interface{}, id string) map[interface{}]interface{} {
	return args[transit.Keyword(id)].(map[interface{}]interface{})
}

func GetStringSeq(args map[interface{}]interface{}, id string) []string {
	seq := args[transit.Keyword(id)].([]interface{})
	result := make([]string, len(seq))
	for i, v := range seq {
		result[i] = v.(string)
	}
	return result
}

func MkTempIdResult(tempid transit.TaggedValue, resp *resty.Response) interface{} {
	result := make(map[interface{}]interface{})
	tempMap := transit.NewCMap()
	tempMap = tempMap.Put(tempid, transit.Keyword(resp.String()), transit.Equals)
	result[transit.Keyword("fulcro.client.primitives/tempids")] = tempMap
	return result
}

func MkTempResult(tempid transit.TaggedValue, val int64) interface{} {
	result := make(map[interface{}]interface{})
	tempMap := transit.NewCMap()
	tempMap = tempMap.Put(tempid, val, transit.Equals)
	result[transit.Keyword("fulcro.client.primitives/tempids")] = tempMap
	return result
}
