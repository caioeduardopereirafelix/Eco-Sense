package br.com.ecosense.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import br.com.ecosense.api.ApiDtos.ChannelInfo;
import br.com.ecosense.api.ApiDtos.Device;
import br.com.ecosense.api.ApiDtos.DeviceSettings;
import br.com.ecosense.config.EcoSenseProperties;
import br.com.ecosense.service.DeviceCatalog;
import br.com.ecosense.service.DeviceService;
import br.com.ecosense.thingspeak.ChannelMeta;
import br.com.ecosense.thingspeak.ThingSpeakClient;

@RestController
@RequestMapping("/api")
public class DeviceAdminController {

    private final DeviceCatalog catalog;
    private final DeviceService devices;
    private final ThingSpeakClient thingspeak;
    private final EcoSenseProperties props;

    public DeviceAdminController(DeviceCatalog catalog, DeviceService devices, ThingSpeakClient thingspeak,
                                 EcoSenseProperties props) {
        this.catalog = catalog;
        this.devices = devices;
        this.thingspeak = thingspeak;
        this.props = props;
    }

    @GetMapping("/devices/{id}/settings")
    public DeviceSettings settings(@PathVariable String id) {
        return catalog.settings(id);
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public Device create(@Valid @RequestBody DeviceSettings body) {
        return devices.toDevice(catalog.create(body));
    }

    @PutMapping("/devices/{id}")
    public Device update(@PathVariable String id, @Valid @RequestBody DeviceSettings body) {
        return devices.toDevice(catalog.update(id, body));
    }

    @DeleteMapping("/devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        catalog.delete(id);
    }

    @GetMapping("/thingspeak/channels/{channelId}")
    public ChannelInfo channel(@PathVariable long channelId) {
        try {
            ChannelMeta m = thingspeak.channel(channelId, props.thingspeak().readApiKey());
            return new ChannelInfo(m.id(), m.name(), m.description(), m.latitude(), m.longitude(),
                    m.fields(), m.lastEntryAt());
        } catch (RestClientResponseException e) {
            throw ApiException.notFound("Canal " + channelId + " não encontrado no ThingSpeak, ou é privado "
                    + "e a Read API Key (THINGSPEAK_READ_API_KEY) não dá acesso a ele.");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o ThingSpeak agora.");
        }
    }
}
