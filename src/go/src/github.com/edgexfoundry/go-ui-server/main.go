// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"github.com/edgexfoundry/go-ui-server/internal/edgex"
	"github.com/edgexfoundry/go-ui-server/internal/fulcro"
	"strconv"
)

func main() {
	config, err := edgex.LoadConfig("")
	edgex.InitEndpoints(config)

	if err != nil {
		return
	}

	server := fulcro.NewServer()
	server.AddQueryFunc("q/edgex-devices", edgex.Devices)
	server.AddQueryFunc("q/edgex-device-services", edgex.DeviceServices)
	server.AddQueryFunc("q/edgex-schedule-events", edgex.ScheduleEvents)
	server.AddQueryFunc("q/edgex-addressables", edgex.Addressables)
	server.AddQueryFunc("q/edgex-profiles", edgex.Profiles)
	server.AddQueryFunc("q/edgex-profile-yaml", edgex.ProfileYaml)
	server.AddQueryFunc("q/edgex-commands", edgex.Commands)
	server.AddQueryFunc("q/edgex-readings", edgex.DeviceReadings)
	server.AddQueryFunc("show-schedules", edgex.ShowSchedules)
	server.AddQueryFunc("show-exports", edgex.ShowExports)
	server.AddQueryFunc("show-profiles", edgex.ShowProfiles)
	server.AddQueryFunc("show-devices", edgex.ShowDevices)
	server.AddQueryFunc("show-addressables", edgex.ShowAddressables)
	server.AddQueryFunc("show-logs", edgex.ShowLogs)
	server.AddQueryFunc("show-commands", edgex.ShowCommands)
	server.AddQueryFunc("reading-page", edgex.ReadingPage)
	server.AddQueryFunc("endpoint", edgex.Endpoints)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/update-lock-mode", edgex.UpdateLockMode)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/save-endpoints", edgex.SaveEndpoints)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/upload-profile", edgex.UploadProfile)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-profile", edgex.DeleteProfile)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/add-device", edgex.AddDevice)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-device", edgex.DeleteDevice)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/add-addressable", edgex.AddAddressable)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/edit-addressable", edgex.EditAddressable)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-addressable", edgex.DeleteAddressable)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/add-schedule", edgex.AddSchedule)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-schedule", edgex.DeleteSchedule)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/add-schedule-event", edgex.AddScheduleEvent)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-schedule-event", edgex.DeleteScheduleEvent)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/issue-set-command", edgex.IssueSetCommand)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/add-export", edgex.AddExport)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/edit-export", edgex.EditExport)
	server.AddMutationFunc("org.edgexfoundry.ui.manager.api.mutations/delete-export", edgex.DeleteExport)
	router := server.SetupRouter()

	// Listen on all interfaces at specified port
	router.Run(":" + strconv.Itoa(config.Server.Port))
}
