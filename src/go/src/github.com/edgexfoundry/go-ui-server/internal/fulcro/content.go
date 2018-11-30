// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package fulcro

import (
	"net/http"

	"github.com/russolsen/transit"
)

type Transit struct {
	Data interface{}
}

var transitContentType = []string{"application/transit+json"}

func (r Transit) Render(w http.ResponseWriter) (err error) {
	if err = WriteTransit(w, r.Data); err != nil {
		panic(err)
	}
	return
}

func (r Transit) WriteContentType(w http.ResponseWriter) {
	writeContentType(w, transitContentType)
}

func WriteTransit(w http.ResponseWriter, obj interface{}) error {
	writeContentType(w, transitContentType)
	encoder := transit.NewEncoder(w, false)
	return encoder.Encode(obj)
}

func writeContentType(w http.ResponseWriter, value []string) {
	header := w.Header()
	if val := header["Content-Type"]; len(val) == 0 {
		header["Content-Type"] = value
	}
}
