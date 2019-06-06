// Copyright (C) 2018 IOTech Ltd
//
// SPDX-License-Identifier: Apache-2.0

package edgex

import (
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/gin-gonic/gin"
	"io/ioutil"
	"math"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/edgexfoundry/go-ui-server/internal/fulcro"
	"github.com/russolsen/transit"
	"golang.org/x/crypto/bcrypt"

	"gopkg.in/resty.v1"
)

func Login(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	password := fulcro.GetString(args, "password")
	pw_file := os.Getenv("DATA_FILE")
	var existing []byte
	if pw_file == "" {
		fmt.Println("No Password File.")
	  	return nil, errors.New("Password File Path Not defined.")
	}

	saved, err := ioutil.ReadFile(pw_file)
	if err != nil {
		// first login, file not exists
		origin_pw := []byte("admin")
		hashedBytes, err := bcrypt.GenerateFromPassword(origin_pw, bcrypt.DefaultCost)
		existing = hashedBytes
		if err != nil {
			return  nil, err
		}
		err = ioutil.WriteFile(pw_file, hashedBytes, 0644)
		if err != nil {
			fmt.Println(err)
			return  nil, err
		}
	} else {
		expectedPassword := string(saved)
		// remove last '\n' character of expectedPassword
		expectedPassword = strings.TrimSuffix(expectedPassword, "\n")
		existing = []byte(expectedPassword)
	}
	incoming := []byte(password)

	if (bcrypt.CompareHashAndPassword(existing, incoming) != nil) {
		return  nil, errors.New("Invalid Password")
	}

	return nil, nil
}

func ChangePassword(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	oldpw := fulcro.GetString(args, "oldpw")
	newpw := fulcro.GetString(args, "newpw")
	pw_file := os.Getenv("DATA_FILE")
	var existing []byte
	if pw_file == "" {
		fmt.Println("Password File Path Not defined.")
		return nil, errors.New("Password File Path Not defined.")
	}
	saved, err := ioutil.ReadFile(pw_file)
	if err != nil {
		// first login, file not exists
		origin_pw := []byte("admin")
		hashedBytes, err := bcrypt.GenerateFromPassword(origin_pw, bcrypt.DefaultCost)
		existing = hashedBytes
		if err != nil {
			return  nil, err
		}
		err = ioutil.WriteFile(pw_file, hashedBytes, 0644)
		if err != nil {
			fmt.Println(err)
			return  nil, err
		}
	} else {
		expectedPassword := string(saved)
		// remove last '\n' character of expectedPassword
		expectedPassword = strings.TrimSuffix(expectedPassword, "\n")
		existing = []byte(expectedPassword)
	}
	incoming := []byte(oldpw)
	err = bcrypt.CompareHashAndPassword(existing, incoming)
	if err != nil {
		fmt.Println(err)
		return  nil, errors.New("Invalid Current Password")
	}

	modifying := []byte(newpw)
	hashedBytes, err := bcrypt.GenerateFromPassword(modifying, bcrypt.DefaultCost)
	if err != nil {
		return  nil, err
	}
	err = ioutil.WriteFile(pw_file, hashedBytes, 0644)
	if err != nil {
		fmt.Println(err)
		return  nil, err
	}
	return nil, nil
}

func AddUpload(r *gin.Engine) {
	var fileUpLoadId int64 = 0

	r.POST("/file-uploads", func(c *gin.Context) {
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
			result := make(map[transit.Symbol]interface{})
			result[transit.Symbol("upload")] = fulcro.MkTempResult(req, fileUpLoadId)
			c.Render(http.StatusOK, fulcro.Transit{Data: result})
		}

		fileUpLoadId += 1
	})
}

func getDevices() (interface{}, error) {
	var data []map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "device")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "device")
		result = fulcro.Remove(result, "profile", "deviceResources")
		result = fulcro.Remove(result, "profile", "resources")
		result = fulcro.Remove(result, "profile", "commands")
		result = fulcro.MakeKeyword(result, "id")
		result = fulcro.MakeKeyword(result, "adminState")
		result = fulcro.MakeKeyword(result, "operatingState")
		result = fulcro.MakeKeyword(result, "service", "adminState")
		result = fulcro.MakeKeyword(result, "service", "operatingState")
		result = fulcro.MakeKeyword(result, "profile", "id")
	}
	return result, err
}

func Devices(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(getDevices())
}

func getDeviceServices() (interface{}, error) {
	var data []map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "deviceservice")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "device-service")
		result = fulcro.MakeKeyword(result, "id")
		result = fulcro.MakeKeyword(result, "adminState")
		result = fulcro.MakeKeyword(result, "operatingState")
		result = fulcro.MakeKeyword(result, "addressable", "id")
	}
	return result, err
}

func DeviceServices(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(getDeviceServices())
}

func ScheduleEvents(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var data []map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "scheduleevent")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result := fulcro.AddType(data, "schedule-event")
		result = fulcro.MakeKeyword(result, "id")
		result = fulcro.MakeKeyword(result, "adminState")
		result = fulcro.MakeKeyword(result, "operatingState")
		result = fulcro.MakeKeyword(result, "addressable", "id")
	}
	return fulcro.Keywordize(result, err)
}

func getAddressables() (interface{}, error) {
	var data []map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "addressable")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "addressable")
		result = fulcro.MakeKeyword(result, "id")
	}
	return result, err
}

func Addressables(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(getAddressables())
}

func getProfiles() (interface{}, error) {
	var data []map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "deviceprofile")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "device-profile")
		result = fulcro.MakeKeyword(result, "id")
	}
	return result, err
}

func doGet(getInfo interface{}) interface{} {
	var data map[string]interface{}
	var result [][2]string
	info := getInfo.(map[string]interface{})
	url := info["url"].(string)
	resp, _ := resty.R().Get(url)
	json.Unmarshal(resp.Body(), &data)
	readings, _ := data["readings"]
	rds := readings.([]interface{})
	result = make([][2]string, len(rds))
	for i, r := range rds {
		reading := r.(map[string]interface{})
		result[i] = [2]string{reading["name"].(string), fmt.Sprintf("%v", reading["value"])}
	}
	return result
}

func applyGets(data interface{}) interface{} {
	commands := data.([]map[string]interface{})
	count := 0
	for i, cmd := range commands {
		haveData := false
		get, haveGet := cmd["get"]
		if haveGet {
			delete(commands[i], "get")
			resp, haveResp := get.(map[string]interface{})["responses"]
			if haveResp && resp != nil {
				values := doGet(get)
				commands[i]["value"] = values
				count += len(values.([][2]string))
				haveData = true
			}
		}
		if !haveData {
			dummyValue := make([][2]string, 1)
			dummyValue[0] = [2]string{cmd["name"].(string), "N/A"}
			commands[i]["value"] = dummyValue
			count++
		}
	}
	result := make([]map[string]interface{}, count)
	pos := 0
	for _, cmd := range commands {
		values := cmd["value"].([][2]string)
		for i, val := range values {
			c := make(map[string]interface{})
			for k, v := range cmd {
				c[k] = v
			}
			result[pos] = c
			result[pos]["value"] = val
			result[pos]["pos"] = i
			result[pos]["size"] = len(values)
			pos++
		}
	}
	return result
}

func getCommands(id transit.Keyword) (interface{}, error) {
	var data map[string]interface{}
	var result interface{}

	resp, err := resty.R().Get(getEndpoint(ClientCommand) + "device/" + string(id))

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		commands := data["commands"].([]interface{})
		result = make([]map[string]interface{}, len(commands))
		for i, cmd := range commands {
			result.([]map[string]interface{})[i] = cmd.(map[string]interface{})
		}
		result = fulcro.AddType(result, ClientCommand)
		result = fulcro.MakeKeyword(result, "id")
		result = applyGets(result)
	}
	return result, err
}

func Commands(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(getCommands(fulcro.GetKeyword(args, "id")))
}

func getReadingsInTimeRange(name string, from int64, to int64) (interface{}, error) {
	const batchSize = 100
	const maxRequests = 100
	result := make([]interface{}, batchSize * maxRequests)
	ids := make(map[string]bool)
	pos := 0
	limit := maxRequests
	toStr := strconv.FormatInt(to, 10)
	batchStr := strconv.FormatInt(batchSize, 10)
	var count int
	for ok := true; ok; ok = (count == batchSize) && (limit > 0) {
		fromStr := strconv.FormatInt(from, 10)
		resp, err := resty.R().Get(getEndpoint(ClientData) + "reading/" + fromStr + "/" + toStr + "/" + batchStr)
		if err != nil {
			return nil, err
		}
		var data []map[string]interface{}
		json.Unmarshal(resp.Body(), &data)
		readings := fulcro.AddType(data, "reading").([]map[string]interface{})
		count = len(readings)
		if (count > 0) {
			from = int64(readings[count-1]["created"].(float64))
		}
		for _, reading := range readings {
			if reading["device"].(string) != name {
				continue
			}
			//check floatEncoding
			valueDesName := reading["name"].(string)
			resp, err := resty.R().Get(getEndpoint(ClientData) + "valuedescriptor/name/" + valueDesName)
			var valueDesData map[string]interface{}
			json.Unmarshal(resp.Body(), &valueDesData)
			if err == nil && valueDesData["floatEncoding"] != nil && valueDesData["floatEncoding"].(string) == "Base64"{
				float32_value := reading["value"].(string)
				decodeValue, _ := base64.StdEncoding.DecodeString(float32_value)
				bits := binary.LittleEndian.Uint32(decodeValue)
				reading["value"] = math.Float32frombits(bits)
			}
			id := reading["id"].(string)
			if !ids[id] {
				ids[id] = true
				result[pos] = fulcro.MakeKeyword(reading, "id")
				pos++
			}
		}
		limit--
	}
	return result[:pos], nil
}

func DeviceReadings(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	name := fulcro.GetString(args, "name")
	from := fulcro.GetInt(args, "from")
	to := fulcro.GetInt(args, "to")
	return fulcro.Keywordize(getReadingsInTimeRange(name, from, to))
}

func Profiles(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	return fulcro.Keywordize(getProfiles())
}

func ProfileYaml(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	var err error

	id := fulcro.GetKeyword(args, "id")
	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "deviceprofile/yaml/" + string(id))

	if err == nil {
		m := make(map[string]interface{})
		m["yaml"] = resp.String()
		arr := make([]interface{}, 1)
		arr[0] = m
		result = arr
	}
	return fulcro.Keywordize(result, err)
}

func addDefault(data interface{}, keys ...string) interface{} {
	schedules := data.([]map[string]interface{})
	for _, s := range schedules {
		for _, key := range keys {
			v := s[key]
			if v == nil {
				s[key] = 0
			}
		}
	}
	return schedules
}

func getSchedules() (interface{}, error) {
	var result interface{}
	var data []map[string]interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "schedule")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "schedule")
		result = fulcro.MakeKeyword(result, "id")
		result = addDefault(result, "start", "end")
	}
	return result, err
}

func getScheduleEvents() (interface{}, error) {
	var result interface{}
	var data []map[string]interface{}

	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "scheduleevent")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		result = fulcro.AddType(data, "schedule-event")
		result = fulcro.MakeKeyword(result, "id")
		result = fulcro.MakeKeyword(result, "addressable", "id")
	}
	return result, err
}

func ShowSchedules(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	result := make(map[string]interface{})
	result["content"], err = getSchedules()
	if err == nil {
		result["events"], err = getScheduleEvents()
	}
	return fulcro.Keywordize(result, err)
}

func getNotifyInTimeRange(from int64, to int64, notifyType string, keys []string, slug string) (interface{}, error) {
	const batchSize = 100
	const maxRequests = 100
	result := make([]interface{}, batchSize * maxRequests)
	ids := make(map[transit.Keyword]bool)
	pos := 0
	limit := maxRequests
	toStr := strconv.FormatInt(to, 10)
	batchStr := strconv.FormatInt(batchSize, 10)
	var count int
	for ok := true; ok; ok = (count == batchSize) && (limit > 0) {
		fromStr := strconv.FormatInt(from, 10)
		var url string
		if slug == "" {
			url = getEndpoint(ClientNotifications) + notifyType + "/start/" + fromStr + "/end/" + toStr + "/" + batchStr
		} else {
			url = getEndpoint(ClientNotifications) + notifyType + "/slug/" + slug + "/start/" + fromStr + "/end/" + toStr + "/" + batchStr
		}
		resp, err := resty.R().Get(url)

		if err != nil {
			return nil, err
		}

		var data []map[string]interface{}
		json.Unmarshal(resp.Body(), &data)
		logs := fulcro.AddType(data, notifyType).([]map[string]interface{})
		inc := 0
		var last float64 = 0
		for i, log := range logs {
			ts := log["created"].(float64)
			if ts != last {
				inc = 0
			}
			for _, key := range keys {
				if value, ok := log[key]; ok {
					logs[i][key] = transit.Keyword(value.(string))
				}
			}
			inc++
			last = ts
		}
		count = len(logs)
		if count > 0 {
			from = int64(logs[count-1]["created"].(float64))
			for _, entry := range logs {
				id := entry["id"].(transit.Keyword)
				if !ids[id] {
					ids[id] = true
					result[pos] = entry
					pos++
				}
			}
		}
		limit--
	}
	return result[:pos], nil
}

func ShowNotifications(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	result := make(map[string]interface{})
	start := fulcro.GetInt(args, "start")
	end := fulcro.GetInt(args, "end")
	var err error
	keys := []string{"id", "category", "severity", "status"}
	result["content"], err = getNotifyInTimeRange(start, end, "notification", keys, "")
	return fulcro.Keywordize(result, err)
}

func ShowSubscriptions(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var data []map[string]interface{}
	result := make(map[string]interface{})
	resp, err := resty.R().Get(getEndpoint(ClientNotifications) + "subscription")

	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		subscriptions := fulcro.AddType(data, "subscription")
		subscriptions = fulcro.MakeKeyword(subscriptions, "id")
		result["content"] = subscriptions
	}
	return fulcro.Keywordize(result, err)
}

func ShowTransmissions(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	result := make(map[string]interface{})
	slug := fulcro.GetString(args, "slug")
	var start int64
	var end int64
  var err error

	if slug != "" {
		// show the transmissions from a week ago till now if get by slug
		end = int64(time.Now().UnixNano())/int64(time.Millisecond)
		start = int64(time.Now().AddDate(0, 0, -7).UnixNano())/int64(time.Millisecond)
	} else {
		start = fulcro.GetInt(args, "start")
		end = fulcro.GetInt(args, "end")
	}
	keys := []string{"id", "status"}
	result["content"], err = getNotifyInTimeRange(start, end, "transmission", keys, slug)
	return fulcro.Keywordize(result, err)
}

func ShowExports(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var data []map[string]interface{}
	result := make(map[string]interface{})

	resp, err := resty.R().Get(getEndpoint(ClientExport) + "registration")
	if err == nil {
		json.Unmarshal(resp.Body(), &data)
		exports := fulcro.AddType(data, "export")
		exports = fulcro.MakeKeyword(exports, "id")
		exports = fulcro.MakeKeyword(exports, "destination")
		exports = fulcro.MakeKeyword(exports, "format")
		exports = fulcro.MakeKeyword(exports, "compression")
		exports = fulcro.MakeKeyword(exports, "encryption", "encryptionAlgorithm")
		result["content"] = exports
	}
	return fulcro.Keywordize(result, err)
}

func ShowProfiles(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	result := make(map[string]interface{})
	result["content"], err = getProfiles()
	return fulcro.Keywordize(result, err)
}

func ShowDevices(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	result := make(map[string]interface{})
	result["content"], err = getDevices()
	if err == nil {
		result["services"], err = getDeviceServices()
	}
	if err == nil {
		result["schedules"], err = getSchedules()
	}
	if err == nil {
		result["addressables"], err = getAddressables()
	}
	if err == nil {
		result["profiles"], err = getProfiles()
	}
	return fulcro.Keywordize(result, err)
}

func ShowAddressables(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	result := make(map[string]interface{})
	result["content"], err = getAddressables()
	return fulcro.Keywordize(result, err)
}

func getLogsInTimeRange(from int64, to int64) (interface{}, error) {
	const batchSize = 100
	const maxRequests = 100
	result := make([]interface{}, batchSize * maxRequests)
	ids := make(map[transit.Keyword]bool)
	pos := 0
	limit := maxRequests
	toStr := strconv.FormatInt(to, 10)
	batchStr := strconv.FormatInt(batchSize, 10)
	var count int
	for ok := true; ok; ok = (count == batchSize) && (limit > 0) {
		fromStr := strconv.FormatInt(from, 10)
		resp, err := resty.R().Get(getEndpoint(ClientLogging) + "logs/" + fromStr + "/" + toStr + "/" + batchStr)

		if err != nil {
			return nil, err
		}

		var data []map[string]interface{}
		json.Unmarshal(resp.Body(), &data)
		logs := fulcro.AddType(data, "log-entry").([]map[string]interface{})
		inc := 0
		var last float64 = 0
		for i, log := range logs {
			ts := log["created"].(float64)
			if ts != last {
				inc = 0
			}
			logs[i]["id"] = transit.Keyword(strconv.FormatInt(int64(ts), 10) + "-" + strconv.Itoa(inc))
			inc++
			last = ts
		}
		count = len(logs)
		if count > 0 {
			from = int64(logs[count-1]["created"].(float64))
			for _, entry := range logs {
				id := entry["id"].(transit.Keyword)
				if !ids[id] {
					ids[id] = true
					result[pos] = entry
					pos++
				}
			}
		}
		limit--
	}
	return result[:pos], nil
}

func ShowLogs(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	start := fulcro.GetInt(args, "start")
	end := fulcro.GetInt(args, "end")
	result := make(map[string]interface{})
	result["content"], err = getLogsInTimeRange(start, end)
	return fulcro.Keywordize(result, err)
}

func ShowCommands(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	id := fulcro.GetKeyword(args, "id")
	result := make(map[string]interface{})
	result["source-device"] = id
	result["commands"], err = getCommands(id)
	return fulcro.Keywordize(result, err)
}

func ReadingPage(params []interface{}, args map[interface{}]interface{}) (interface{}, error) {
	var err error
	result := make(map[string]interface{})
	result["devices"], err = getDevices()
	return fulcro.Keywordize(result, err)
}

func UpdateLockMode(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	mode := fulcro.GetKeyword(args, "mode")
	_, err := resty.R().Put(getEndpoint(ClientCommand) + "device/" + string(id) + "/adminstate/" + string(mode))
	return id, err
}

func UploadProfile(args map[interface{}]interface{}) (interface{}, error) {
	fileId := fulcro.GetInt(args, "file-id")
	fileName := "tmp-" + strconv.FormatInt(fileId, 10)
	_, err := resty.R().
		SetHeader("Content-Type", "application/x-yaml").
		SetFile("file", fileName).
		Post(getEndpoint(ClientMetadata) + "deviceprofile/uploadfile")
	os.Remove(fileName)
	return fileId, err
}

func DeleteProfile(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	_, err := resty.R().Delete(getEndpoint(ClientMetadata) + "deviceprofile/id/" + string(id))
	return id, err
}

type Named struct {
	Name string `json:"name"`
}

type Addressable struct {
	Id        string `json:"id,omitempty"`
	Name      string `json:"name,omitempty"`
	Address   string `json:"address"`
	Protocol  string `json:"protocol,omitempty"`
	Port      int64  `json:"port,omitempty"`
	Path      string `json:"path"`
	Method    string `json:"method,omitempty"`
	Publisher string `json:"publisher,omitempty"`
	Topic     string `json:"topic,omitempty"`
	User      string `json:"user,omitempty"`
	Password  string `json:"password,omitempty"`
	Cert      string `json:"cert,omitempty"`
	Key       string `json:"key,omitempty"`
}

type Device struct {
	Name           string   `json:"name"`
	Description    string   `json:"description"`
	Labels         []string `json:"labels"`
	Profile        Named    `json:"profile"`
	Service        Named    `json:"service"`
	Addressable    Named    `json:"addressable"`
	AdminState     string   `json:"adminState"`
	OperatingState string   `json:"operatingState"`
	Protocols      map[string]interface{} `json:"protocols"`
}

func AddDevice(args map[interface{}]interface{}) (interface{}, error) {
	name := fulcro.GetString(args, "name")
	description := fulcro.GetString(args, "description")
	labels := fulcro.GetStringSeq(args, "labels")
	profileName := fulcro.GetString(args, "profile-name")
	serviceName := fulcro.GetString(args, "service-name")
	protocols := fulcro.GetPrtsMap(args, "protocols")
	addressableName := name + "-addr"
	device := Device{Name: name,
		Description:    description,
		Labels:         labels,
		Profile:        Named{Name: profileName},
		Service:        Named{Name: serviceName},
		Addressable:    Named{Name: addressableName},
		AdminState:     "UNLOCKED",
		OperatingState: "ENABLED",
		Protocols:       protocols,
	}
	address := fulcro.GetString(args, "address")
	protocol := fulcro.GetString(args, "protocol")
	port := fulcro.GetInt(args, "port")
	path := fulcro.GetString(args, "path")
	method := strings.ToUpper(string(fulcro.GetKeyword(args, "method")))
	publisher := fulcro.GetString(args, "publisher")
	topic := fulcro.GetString(args, "topic")
	user := fulcro.GetString(args, "user")
	password := fulcro.GetString(args, "password")
	addressable := Addressable{
		Id:        "",
		Name:      addressableName,
		Address:   address,
		Protocol:  protocol,
		Port:      port,
		Path:      path,
		Method:    method,
		Publisher: publisher,
		Topic:     topic,
		User:      user,
		Password:  password,
		Cert:      "",
		Key:       "",
	}
	_, err := resty.R().SetBody(addressable).Post(getEndpoint(ClientMetadata) + "addressable")
	if err == nil {
		_, err = resty.R().SetBody(device).Post(getEndpoint(ClientMetadata) + "device")
	}
	return nil, err
}

func DeleteDevice(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	resp, err := resty.R().Get(getEndpoint(ClientMetadata) + "device/" + string(id))
	if err == nil {
		var data map[string]interface{}
		json.Unmarshal(resp.Body(), &data)
		service := data["service"].(map[string]interface{})
		addressable := service["addressable"].(map[string]interface{})
		addressableId := addressable["id"].(string)
		_, err = resty.R().Delete(getEndpoint(ClientMetadata) + "device/id/" + string(id))
		if err == nil {
			_, err = resty.R().Delete(getEndpoint(ClientMetadata) + "addressable/id/" + addressableId)
		}
	}
	return id, err
}

func AddAddressable(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	name := fulcro.GetString(args, "name")
	address := fulcro.GetString(args, "address")
	protocol := fulcro.GetString(args, "protocol")
	port := fulcro.GetInt(args, "port")
	path := fulcro.GetString(args, "path")
	method := strings.ToUpper(string(fulcro.GetKeyword(args, "method")))
	publisher := fulcro.GetString(args, "publisher")
	topic := fulcro.GetString(args, "topic")
	user := fulcro.GetString(args, "user")
	password := fulcro.GetString(args, "password")
	cert := fulcro.GetString(args, "cert")
	key := fulcro.GetString(args, "key")
	addressable := Addressable{
		Id:        "",
		Name:      name,
		Address:   address,
		Protocol:  protocol,
		Port:      port,
		Path:      path,
		Method:    method,
		Publisher: publisher,
		Topic:     topic,
		User:      user,
		Password:  password,
		Cert:      cert,
		Key:       key,
	}
	resp, err := resty.R().SetBody(addressable).Post(getEndpoint(ClientMetadata) + "addressable")
	if err == nil {
		result = fulcro.MkTempIdResult(tempid, resp)
	}
	return result, err
}

func EditAddressable(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	address := fulcro.GetString(args, "address")
	protocol := fulcro.GetString(args, "protocol")
	port := fulcro.GetInt(args, "port")
	path := fulcro.GetString(args, "path")
	method := strings.ToUpper(string(fulcro.GetKeyword(args, "method")))
	publisher := fulcro.GetString(args, "publisher")
	topic := fulcro.GetString(args, "topic")
	user := fulcro.GetString(args, "user")
	password := fulcro.GetString(args, "password")
	cert := fulcro.GetString(args, "cert")
	key := fulcro.GetString(args, "key")
	addressable := Addressable{
		Id:        string(id),
		Name:      "",
		Address:   address,
		Protocol:  protocol,
		Port:      port,
		Path:      path,
		Method:    method,
		Publisher: publisher,
		Topic:     topic,
		User:      user,
		Password:  password,
		Cert:      cert,
		Key:       key,
	}
	_, err := resty.R().SetBody(addressable).Put(getEndpoint(ClientMetadata) + "addressable")
	return id, err
}

func DeleteAddressable(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	_, err := resty.R().Delete(getEndpoint(ClientMetadata) + "addressable/id/" + string(id))
	return id, err
}

type Schedule struct {
	Name      string `json:"name,omitempty"`
	Start     string `json:"start"`
	End       string `json:"end"`
	Frequency string `json:"frequency"`
	RunOnce   bool   `json:"run-once"`
}

func AddSchedule(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	name := fulcro.GetString(args, "name")
	start := fulcro.GetString(args, "start")
	end := fulcro.GetString(args, "end")
	frequency := fulcro.GetString(args, "frequency")
	runOnce := fulcro.GetBool(args, "run-once")
	schedule := Schedule{
		Name:      name,
		Start:     start,
		End:       end,
		Frequency: frequency,
		RunOnce:   runOnce,
	}
	resp, err := resty.R().SetBody(schedule).Post(getEndpoint(ClientMetadata) + "schedule")
	if err == nil {
		result = fulcro.MkTempIdResult(tempid, resp)
	}
	return result, err
}

func DeleteSchedule(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	_, err := resty.R().Delete(getEndpoint(ClientMetadata) + "schedule/id/" + string(id))
	return id, err
}

type ScheduleEvent struct {
	Name        string `json:"name,omitempty"`
	Addressable Named  `json:"addressable"`
	Parameters  string `json:"parameters"`
	Schedule    string `json:"schedule"`
	Service     string `json:"service"`
}

func AddScheduleEvent(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	name := fulcro.GetString(args, "name")
	addressableName := fulcro.GetString(args, "addressable-name")
	parameters := fulcro.GetString(args, "parameters")
	schedule := fulcro.GetString(args, "schedule-name")
	service := fulcro.GetString(args, "service-name")
	scheduleEvent := ScheduleEvent{
		Name:        name,
		Addressable: Named{Name: addressableName},
		Parameters:  parameters,
		Schedule:    schedule,
		Service:     service,
	}
	resp, err := resty.R().SetBody(scheduleEvent).Post(getEndpoint(ClientMetadata) + "scheduleevent")
	if err == nil {
		result = fulcro.MkTempIdResult(tempid, resp)
	}
	return result, err
}

func DeleteScheduleEvent(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	_, err := resty.R().Delete(getEndpoint(ClientMetadata) + "scheduleevent/id/" + string(id))
	return id, err
}

type Encryption struct {
	EncryptionAlgorithm string `json:"encryptionAlgorithm"`
	EncryptionKey       string `json:"encryptionKey,omitempty"`
	InitializingVector  string `json:"initializingVector,omitempty"`
}

type Filter struct {
	DeviceIdentifiers []string `json:"deviceIdentifiers"`
	ValueDescriptorIdentifiers []string `json:"valueDescriptorIdentifiers"`
}

type Export struct {
	Id          string `json:"id,omitempty"`
	Name        string `json:"name,omitempty"`
	Addr        Addressable `json:"addressable"`
	Format      string `json:"format"`
	Destination string `json:"destination"`
	Compression string `json:"compression"`
	Encrypt     Encryption `json:"encryption"`
	Filt        Filter `json:"filter"`
	Enable      bool `json:"enable"`
}

func AddExport(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	name := fulcro.GetString(args, "name")
	export := Export{
		Id: "",
		Name: name,
		Addr: Addressable{
			Id:        "",
			Name:      name + "-addr",
			Address:   fulcro.GetString(args, "address"),
			Protocol:  fulcro.GetString(args, "protocol"),
			Port:      fulcro.GetInt(args, "port"),
			Path:      fulcro.GetString(args, "path"),
			Method:    strings.ToUpper(string(fulcro.GetKeyword(args, "method"))),
			Publisher: fulcro.GetString(args, "publisher"),
			Topic:     fulcro.GetString(args, "topic"),
			User:      fulcro.GetString(args, "user"),
			Password:  fulcro.GetString(args, "password"),
			Cert:      fulcro.GetString(args, "cert"),
			Key:       fulcro.GetString(args, "key"),
		},
		Format:      fulcro.GetKeywordAsString(args, "format"),
		Destination: fulcro.GetKeywordAsString(args, "destination"),
		Compression: fulcro.GetKeywordAsString(args, "compression"),
		Encrypt: Encryption{
			EncryptionAlgorithm: fulcro.GetKeywordAsString(args, "encryptionAlgorithm"),
			EncryptionKey:       fulcro.GetString(args, "encryptionKey"),
			InitializingVector:  fulcro.GetString(args, "initializingVector"),
		},
		Filt: Filter{
			DeviceIdentifiers: fulcro.GetStringSeq(args, "device-filter"),
			ValueDescriptorIdentifiers: fulcro.GetStringSeq(args, "reading-filter"),
		},
		Enable: fulcro.GetBool(args, "enable"),
	}
	resp, err := resty.R().SetBody(export).Post(getEndpoint(ClientExport) + "registration")
	if err == nil {
		result = fulcro.MkTempIdResult(tempid, resp)
	}
	return result, err
}

func EditExport(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	name := fulcro.GetString(args, "name")
	export := Export{
		Id: string(id),
		Name: "",
		Addr: Addressable{
			Id:        "",
			Name:      name + "-addr",
			Address:   fulcro.GetString(args, "address"),
			Protocol:  fulcro.GetString(args, "protocol"),
			Port:      fulcro.GetInt(args, "port"),
			Path:      fulcro.GetString(args, "path"),
			Method:    strings.ToUpper(string(fulcro.GetKeyword(args, "method"))),
			Publisher: fulcro.GetString(args, "publisher"),
			Topic:     fulcro.GetString(args, "topic"),
			User:      fulcro.GetString(args, "user"),
			Password:  fulcro.GetString(args, "password"),
			Cert:      fulcro.GetString(args, "cert"),
			Key:       fulcro.GetString(args, "key"),
		},
		Format:      fulcro.GetKeywordAsString(args, "format"),
		Destination: fulcro.GetKeywordAsString(args, "destination"),
		Compression: fulcro.GetKeywordAsString(args, "compression"),
		Encrypt: Encryption{
			EncryptionAlgorithm: fulcro.GetKeywordAsString(args, "encryptionAlgorithm"),
			EncryptionKey:       fulcro.GetString(args, "encryptionKey"),
			InitializingVector:  fulcro.GetString(args, "initializingVector"),
		},
		Filt: Filter{
			DeviceIdentifiers: fulcro.GetStringSeq(args, "device-filter"),
			ValueDescriptorIdentifiers: fulcro.GetStringSeq(args, "reading-filter"),
		},
		Enable: fulcro.GetBool(args, "enable"),
	}
	_, err := resty.R().SetBody(export).Put(getEndpoint(ClientExport) + "registration")
	return id, err
}

func DeleteExport(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	_, err := resty.R().Delete(getEndpoint(ClientExport) + "registration/id/" + string(id))
	return id, err
}

type Notification struct {
	Id          string `json:"id,omitempty"`
	Slug        string `json:"slug"`
	Sender      string `json:"sender"`
	Category    string `json:"category"`
	Severity    string `json:"severity"`
	Content     string `json:"content"`
	Description string `json:"description,omitempty"`
	Labels    []string `json:"labels"`
}

func AddNotification(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	notify := Notification{
		Id: "",
		Slug: fulcro.GetString(args, "slug"),
		Description: fulcro.GetString(args, "description"),
		Sender: fulcro.GetString(args, "sender"),
		Category: fulcro.GetKeywordAsString(args, "category"),
		Severity: fulcro.GetKeywordAsString(args, "severity"),
		Content: fulcro.GetString(args,"content"),
		Labels: fulcro.GetStringSeq(args, "labels"),
	}
	resp, err := resty.R().SetBody(notify).Post(getEndpoint(ClientNotifications) + "notification")
	if err == nil {
		result = fulcro.MkTempIdResult(tempid, resp)
	}
	return result, err
}

func DeleteNotification(args map[interface{}]interface{}) (interface{}, error) {
	slug := fulcro.GetString(args, "slug")
	_, err := resty.R().Delete(getEndpoint(ClientNotifications) + "notification/slug/" + slug)
	return slug, err
}

type Channel struct {
	Type            string `json:"type,omitempty"`  // REST or EMAIL
	MailAddresses []string    `json:"mailAddresses,omitempty"`
	Url             string `json:"url,omitempty"`
}

type Subscription struct {
	Id                     string `json:"id,omitempty"`
	Slug                   string `json:"slug"`
	Receiver               string `json:"receiver"`
	Description            string `json:"description,omitempty"`
	SubscribedCategories []string `json:"subscribedCategories,omitempty"`
	SubscribedLabels     []string `json:"subscribedLabels,omitempty"`
	Channels             []interface{} `json:"channels"`
}

func AddSubscription(args map[interface{}]interface{}) (interface{}, error) {
	var result interface{}
	tempid := fulcro.GetTempId(args, "tempid")
	slug := fulcro.GetString(args, "slug")

	subscription := Subscription{
		Id: "",
		Slug: slug,
		Description: fulcro.GetString(args, "description"),
		Receiver: fulcro.GetString(args, "receiver"),
		SubscribedCategories: fulcro.GetStringSeq(args, "subscribedCategories"),
		SubscribedLabels: fulcro.GetStringSeq(args, "subscribedLabels"),
		Channels: getChannelSeq(args, "channels"),
	}
	_, err := resty.R().SetBody(subscription).Post(getEndpoint(ClientNotifications) + "subscription")
	if err == nil {
		resp, err := resty.R().Get(getEndpoint(ClientNotifications) + "subscription/slug/" + slug)
		if err == nil {
			var data map[string]interface{}
			json.Unmarshal(resp.Body(), &data)
			id := data["id"].(string)
			result = fulcro.MkTempResult(tempid, transit.Keyword(id))
		}
	}
	return result, err
}

func EditSubscription(args map[interface{}]interface{}) (interface{}, error) {
	id := fulcro.GetKeyword(args, "id")
	subscription := Subscription{
		Id: string(id),
		Slug: fulcro.GetString(args, "slug"),
		Description: fulcro.GetString(args, "description"),
		Receiver: fulcro.GetString(args, "receiver"),
		SubscribedCategories: fulcro.GetStringSeq(args, "subscribedCategories"),
		SubscribedLabels: fulcro.GetStringSeq(args, "subscribedLabels"),
		Channels: getChannelSeq(args, "channels"),
	}
	_, err := resty.R().SetBody(subscription).Put(getEndpoint(ClientNotifications) + "subscription")
	return id, err
}

func DeleteSubscription(args map[interface{}]interface{}) (interface{}, error) {
	slug := fulcro.GetString(args, "slug")
	_, err := resty.R().Delete(getEndpoint(ClientNotifications) + "subscription/slug/" + slug)
	return slug, err
}

func getChannelSeq(args map[interface{}]interface{}, id string) []interface{} {
	outer := args[transit.Keyword(id)].([]interface{})
	result := make([]interface{}, len(outer))
	for i, s := range outer {
		seq := s.(map[interface{}]interface{})
		var channel Channel
		for key, v := range seq {
			switch key {
			case transit.Keyword("type"):
				channel.Type = v.(string)
			case transit.Keyword("url"):
				channel.Url = v.(string)
			case transit.Keyword("mailAddresses"):
				arr := v.([]interface{})
				emails := make([]string, len(arr))
				for j, email := range arr {
					emails[j] = email.(string)
				}
				//channel.MailAddresses = v.([]string)
				channel.MailAddresses = emails
			}
		}
		result[i] = channel
	}
	return result
}

func getValueSeq(args map[interface{}]interface{}, id string) [][]interface{} {
	outer := args[transit.Keyword(id)].([]interface{})
	result := make([][]interface{}, len(outer))
	for i, s := range outer {
		seq := s.([]interface{})
		result[i] = make([]interface{}, len(seq))
		for j, v := range seq {
			result[i][j] = v
		}
	}
	return result
}

func IssueSetCommand(args map[interface{}]interface{}) (interface{}, error) {
	url := fulcro.GetString(args, "url")
	values := getValueSeq(args, "values")
	data := make(map[string]interface{}, len(values))
	for _, v := range values {
		data[v[0].(string)] = v[2]
	}
	_, err := resty.R().SetBody(data).Put(url)
	return nil, err
}
