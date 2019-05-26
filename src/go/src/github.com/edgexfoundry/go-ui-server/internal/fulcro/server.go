// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package fulcro

import (
	"container/list"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/russolsen/transit"
)

type QueryFunc func(params []interface{}, args map[interface{}]interface{}) interface{}

type MutationFunc func(args map[interface{}]interface{}) interface{}

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

func (s Server) InvokeQueryFunc(key transit.Keyword, params []interface{}, args map[interface{}]interface{}) interface{} {
	var result interface{} = nil
	f, ok := s.handlers[key]
	if ok {
		result = f(params, args)
	}
	return result
}

func (s Server) AddMutationFunc(key transit.Symbol, f MutationFunc) {
	s.mutators[key] = f
}

func (s Server) InvokeMutatorFunc(key transit.Symbol, args map[interface{}]interface{}) interface{} {
	var result interface{} = nil
	f, ok := s.mutators[key]
	if ok {
		result = f(args)
	}
	return result
}

func (s Server) rootQuery(query map[interface{}]interface{}, args map[interface{}]interface{}, result *transit.CMap) {
	for k, p := range query {
		key := k.(transit.Keyword)
		params := p.([]interface{})
		result.Append(key, s.InvokeQueryFunc(key, params, args))
	}
}

func (s Server) mutation(op *list.List, result map[transit.Symbol]interface{}) {
	key := op.Front().Value.(transit.Symbol)
	args := op.Front().Next().Value.(map[interface{}]interface{})
	// fmt.Println("mutation", key, args)
	result[key] = s.InvokeMutatorFunc(key, args)
}

func (s Server) entityQuery(query *transit.CMap, args map[interface{}]interface{}, result *transit.CMap) {
	for _, e := range query.Entries {
		key := e.Key.([]interface{})[0].(transit.Keyword)
		params := e.Value.([]interface{})
		result.Append(e.Key, s.InvokeQueryFunc(key, params, args))
	}
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
	var fileUpLoadId int64 = 0

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
				for _, q := range req {
					switch t := q.(type) {
					case map[interface{}]interface{}:
						if result == nil {
							result = transit.NewCMap()
						}
						s.rootQuery(t, nil, result.(*transit.CMap))
					case *transit.CMap:
						if result == nil {
							result = transit.NewCMap()
						}
						s.entityQuery(t, nil, result.(*transit.CMap))
					case *list.List:
						switch head := t.Front().Value.(type) {
						case map[interface{}]interface{}:
							if result == nil {
								result = transit.NewCMap()
							}
							args := t.Front().Next().Value.(map[interface{}]interface{})
							s.rootQuery(head, args, result.(*transit.CMap))
						case *transit.CMap:
							if result == nil {
								result = transit.NewCMap()
							}
							args := t.Front().Next().Value.(map[interface{}]interface{})
							s.entityQuery(head, args, result.(*transit.CMap))
						default:
							if result == nil {
								result = make(map[transit.Symbol]interface{})
							}
							s.mutation(t, result.(map[transit.Symbol]interface{}))
						}
					default:
						fmt.Printf("unknown query %v %T\n", t, t)
					}
				}
				if result != nil {
					c.Render(http.StatusOK, Transit{Data: result})
				} else {
					c.AbortWithError(http.StatusBadRequest, err).SetType(gin.ErrorTypeRender)
				}
			}
		} else {
			c.AbortWithError(http.StatusBadRequest, err).SetType(gin.ErrorTypeBind)
		}
	})

	r.POST("/file-upload", func(c *gin.Context) {
		file, err := c.FormFile("file")
		if err != nil {
			c.String(http.StatusBadRequest, fmt.Sprintf("get form err: %s", err.Error()))
			return
		}

		if err := c.SaveUploadedFile(file, "tmp-"+strconv.FormatInt(fileUpLoadId, 10)); err != nil {
			c.String(http.StatusBadRequest, fmt.Sprintf("upload file err: %s", err.Error()))
			return
		}

		decoder := transit.NewDecoder(strings.NewReader(c.PostForm("id")))
		obj, err := decoder.Decode()
		if err == nil {
			req := obj.(transit.TaggedValue)
			result := MkTempResult(req, fileUpLoadId)
			c.Render(http.StatusOK, Transit{Data: result})
		}

		fileUpLoadId += 1
	})

	var paths = []string {
		"/",
		"/info/*id",
		"/command",
		"/reading",
		"/profile",
		"/schedule",
		"/schedule-event",
		"/schedule-event-info/*id",
		"/profile-yaml",
		"/addressable",
		"/notification",
		"/subscription",
		"/transmission",
		"/export",
		"/log",
	}

	SPAFile(r, paths, "./assets/index.html")
	r.Static("/js", "./assets/js")
	r.Static("/css", "./assets/css")
	r.Static("/img", "./assets/img")
	r.Static("/fonts", "./assets/fonts")

	return r
}
