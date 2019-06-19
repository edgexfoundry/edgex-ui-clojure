// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package edgex

import (
	"github.com/edgexfoundry/go-ui-server/internal/fulcro"
	"github.com/russolsen/transit"
)

var endpoints = make(map[string]interface{})

func getEndpoint(service string) string {
	return HttpScheme + endpoints[service].(string) + APIv1Prefix + "/"
}

func InitEndpoints(config *Config) {
	endpoints[ClientData] = config.Clients["Data"].Endpoint()
	endpoints[ClientMetadata] = config.Clients["Metadata"].Endpoint()
	endpoints[ClientCommand] = config.Clients["Command"].Endpoint()
	endpoints[ClientLogging] = config.Clients["Logging"].Endpoint()
	endpoints[ClientExport] = config.Clients["Export"].Endpoint()
	endpoints[ClientNotifications] = config.Clients["Notifications"].Endpoint()
	endpoints[ClientScheduler] = config.Clients["Scheduler"].Endpoint()
}

func SaveEndpoints(args map[interface{}]interface{}) (interface{}, error) {
	endpoints[ClientData] = args[transit.Keyword(ClientData)]
	endpoints[ClientMetadata] = args[transit.Keyword(ClientMetadata)]
	endpoints[ClientCommand] = args[transit.Keyword(ClientCommand)]
	endpoints[ClientLogging] = args[transit.Keyword(ClientLogging)]
	endpoints[ClientExport] = args[transit.Keyword(ClientExport)]
	endpoints[ClientNotifications] = args[transit.Keyword(ClientNotifications)]
	endpoints[ClientScheduler] = args[transit.Keyword(ClientScheduler)]
	return nil, nil
}

func Endpoints(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(endpoints, nil)
}