// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package edgex

import (
	"fmt"
	"github.com/BurntSushi/toml"
	"io/ioutil"
	"os"
	"strconv"
)

// ClientInfo provides the host and port of another service in the eco-system.
type ClientInfo struct {
	// Host is the hostname or IP address of a service.
	Host string
	// Port defines the port on which to access a given service
	Port int
	// Protocol indicates the protocol to use when accessing a given service
	Protocol string
	// Timeout specifies a timeout (in milliseconds) for
	// processing REST calls from other services.
	// Not currently used.
	Timeout int
}

type Config struct {
	// Port defines the port on which the web server should listen
	Server struct {
		Port int
	}
	// Clients is a map of services used by a DS.
	Clients map[string]ClientInfo
}

func (client ClientInfo) Endpoint() string {
	return client.Host + ":" + strconv.Itoa(client.Port)
}

// Load config (based on EdgeX Go SDK code)
func LoadConfig(confDir string) (config *Config, err error) {
	fmt.Fprintf(os.Stdout, "LoadConfig confDir: %s\n", confDir)
	confName := "configuration.toml"

	if len(confDir) == 0 {
		confDir = "./res"
	}

	path := confDir + "/" + confName

	// As the toml package can panic if TOML is invalid,
	// or elements are found that don't match members of
	// the given struct, use a defered func to recover
	// from the panic and output a useful error.
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("could not load configuration file; invalid TOML (%s)", path)
		}
	}()

	config = &Config{}
	contents, err := ioutil.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("could not load configuration file (%s): %v", path, err.Error())
	}

	// Decode the configuration from TOML
	//
	// TODO: invalid input can cause a SIGSEGV fatal error (INVESTIGATE)!!!
	//       - test missing keys, keys with wrong type, ...
	err = toml.Unmarshal(contents, config)
	if err != nil {
		return nil, fmt.Errorf("unable to parse configuration file (%s): %v", path, err.Error())
	}

	return config, nil
}
