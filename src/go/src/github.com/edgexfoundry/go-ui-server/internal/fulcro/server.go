// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package fulcro

import (
	"container/list"
	"fmt"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/russolsen/transit"
)

type QueryFunc func(params []interface{}, args map[interface{}]interface{}) (interface{}, error)

type MutationFunc func(args map[interface{}]interface{}) (interface{}, error)

type Server struct {
	handlers map[transit.Keyword]QueryFunc
	mutators map[transit.Symbol]MutationFunc
}

func NewServer() Server {
	return Server{
		handlers: make(map[transit.Keyword]QueryFunc),
		mutators: make(map[transit.Symbol]MutationFunc),
	}
}

func (s Server) AddQueryFunc(k string, f QueryFunc) {
	key := transit.Keyword(k)
	s.handlers[key] = f
}

func (s Server) InvokeQueryFunc(key transit.Keyword, params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var result interface{} = nil
	var err error = nil
	f, ok := s.handlers[key]
	if ok {
		result, err = f(params, args)
	}
	return result, err
}

func (s Server) AddMutationFunc(key transit.Symbol, f MutationFunc) {
	s.mutators[key] = f
}

func (s Server) InvokeMutatorFunc(key transit.Symbol, args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	var err error
	f, ok := s.mutators[key]
	if ok {
		result, err = f(args)
	}
	return result, err
}

func (s Server) rootQuery(query map[interface{}]interface{}, args map[interface{}]interface{}, result *transit.CMap) error {
	var err error
	for k, p := range query {
		var val interface{}
		key := k.(transit.Keyword)
		params := p.([]interface{})
		val, err = s.InvokeQueryFunc(key, params, args)
		if err != nil {
			break
		}
		result.Append(key, val)
	}
	return err
}

func (s Server) mutation(op *list.List, result map[transit.Symbol]interface{}) error {
	var err error
	key := op.Front().Value.(transit.Symbol)
	args := op.Front().Next().Value.(map[interface{}]interface{})
	result[key], err = s.InvokeMutatorFunc(key, args)
	return err
}

func (s Server) entityQuery(query *transit.CMap, args map[interface{}]interface{}, result *transit.CMap) error {
	var err error
	for _, e := range query.Entries {
		var val interface{}
		key := e.Key.([]interface{})[0].(transit.Keyword)
		params := e.Value.([]interface{})
		val, err = s.InvokeQueryFunc(key, params, args)
		if err != nil {
			break
		}
		result.Append(e.Key, val)
	}
	return err
}

func SPAFile(group *gin.Engine, relativePaths []string, filepath string) {
	api := group.Group("/")
	handler := func(c *gin.Context) {
		id := c.Param("id")
		if id == "" {
			c.File(filepath)
		} else {
			fmt.Println("id", id)
			switch strings.Split(id, "/")[1] {
			case "js", "css", "img", "fonts":
				id = "./assets" + id
				fmt.Println("id now", id)
				c.File(id)
			default:
				c.File(filepath)
			}
		}
	}
	for _, relativePath := range relativePaths {
		subgroup := api.Group(relativePath)
		{
			subgroup.GET("", handler)	
		}
	}
}

func (s Server) SetupRouter() *gin.Engine {
	gin.DisableConsoleColor()
	r := gin.Default()

	// Ping test
	r.GET("/ping", func(c *gin.Context) {
		c.String(http.StatusOK, "pong")
	})

	r.POST("/api", func(c *gin.Context) {
		req := make([]interface{}, 0)
		var result interface{} = nil
		decoder := transit.NewDecoder(c.Request.Body)
		obj, err := decoder.Decode()
		if err == nil {
			req = obj.([]interface{})
			if req != nil {
				var err error = nil
				for _, q := range req {
					switch t := q.(type) {
					case map[interface{}]interface{}:
						if result == nil {
							result = transit.NewCMap()
						}
						err = s.rootQuery(t, nil, result.(*transit.CMap))
					case *transit.CMap:
						if result == nil {
							result = transit.NewCMap()
						}
						err = s.entityQuery(t, nil, result.(*transit.CMap))
					case *list.List:
						switch head := t.Front().Value.(type) {
						case map[interface{}]interface{}:
							if result == nil {
								result = transit.NewCMap()
							}
							args := t.Front().Next().Value.(map[interface{}]interface{})
							err = s.rootQuery(head, args, result.(*transit.CMap))
						case *transit.CMap:
							if result == nil {
								result = transit.NewCMap()
							}
							args := t.Front().Next().Value.(map[interface{}]interface{})
							err = s.entityQuery(head, args, result.(*transit.CMap))
						default:
							if result == nil {
								result = make(map[transit.Symbol]interface{})
							}
							err = s.mutation(t, result.(map[transit.Symbol]interface{}))
						}
					default:
						fmt.Printf("unknown query %v %T\n", t, t)
					}
				}
				if err != nil {
					errResult := make(map[transit.Keyword]interface{})
					errResult[transit.Keyword("message")] = err.Error()
					result = errResult
					c.Render(http.StatusBadGateway, Transit{Data: result})
				} else if result != nil {
					c.Render(http.StatusOK, Transit{Data: result})
				} else {
					c.AbortWithError(http.StatusBadRequest, err).SetType(gin.ErrorTypeRender)
				}
			}
		} else {
			c.AbortWithError(http.StatusBadRequest, err).SetType(gin.ErrorTypeBind)
		}
	})

	var paths = []string {
		"/",
		"/info/*id",
		"/command/*id",
		"/reading",
		"/profile",
		"/schedule",
		"/schedule-event/*id",
		"/schedule-event-info/*id",
		"/profile-yaml",
		"/addressable",
		"/notification",
		"/subscription",
		"/transmission",
		"/export",
		"/log",
		"/login",
	}

	SPAFile(r, paths, "./assets/index.html")
	r.Static("/js", "./assets/js")
	r.Static("/css", "./assets/css")
	r.Static("/img", "./assets/img")
	r.Static("/fonts", "./assets/fonts")

	return r
}
