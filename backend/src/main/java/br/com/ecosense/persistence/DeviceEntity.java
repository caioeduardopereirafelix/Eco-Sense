package br.com.ecosense.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cadastro de um dispositivo (tabela {@code devices}): dados descritivos + qual canal do ThingSpeak
 * ler e como converter as leituras. As LEITURAS não ficam aqui; vêm do ThingSpeak em tempo real.
 * A Read API Key não é guardada no banco: vem de THINGSPEAK_READ_API_KEY.
 */
@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 120)
    private String location;
    @Column(length = 60)
    private String region;
    private Double latitude;
    private Double longitude;
    private Integer txInterval;
    @Column(length = 40)
    private String type;
    @Column(length = 10)
    private String priority;
    @Column(length = 40)
    private String harvestingType;
    private Double sunHours;

    /* canal do ThingSpeak */
    private Long channelId;
    private Integer historyChunkDays;
    @Column(length = 8)
    private String fieldBattery;
    @Column(length = 8)
    private String fieldConsumption;
    @Column(length = 8)
    private String fieldTemperature;
    @Column(length = 8)
    private String fieldHarvested;

    /* conversões de unidade */
    private Double batteryVoltageMin;
    private Double batteryVoltageMax;
    private Double consumptionScale;
    private Double harvestedScale;
    private Double temperatureScale;


    /** ordem de exibição */
    private Integer position;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Integer getTxInterval() { return txInterval; }
    public void setTxInterval(Integer txInterval) { this.txInterval = txInterval; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getHarvestingType() { return harvestingType; }
    public void setHarvestingType(String harvestingType) { this.harvestingType = harvestingType; }
    public Double getSunHours() { return sunHours; }
    public void setSunHours(Double sunHours) { this.sunHours = sunHours; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Integer getHistoryChunkDays() { return historyChunkDays; }
    public void setHistoryChunkDays(Integer historyChunkDays) { this.historyChunkDays = historyChunkDays; }
    public String getFieldBattery() { return fieldBattery; }
    public void setFieldBattery(String fieldBattery) { this.fieldBattery = fieldBattery; }
    public String getFieldConsumption() { return fieldConsumption; }
    public void setFieldConsumption(String fieldConsumption) { this.fieldConsumption = fieldConsumption; }
    public String getFieldTemperature() { return fieldTemperature; }
    public void setFieldTemperature(String fieldTemperature) { this.fieldTemperature = fieldTemperature; }
    public String getFieldHarvested() { return fieldHarvested; }
    public void setFieldHarvested(String fieldHarvested) { this.fieldHarvested = fieldHarvested; }
    public Double getBatteryVoltageMin() { return batteryVoltageMin; }
    public void setBatteryVoltageMin(Double batteryVoltageMin) { this.batteryVoltageMin = batteryVoltageMin; }
    public Double getBatteryVoltageMax() { return batteryVoltageMax; }
    public void setBatteryVoltageMax(Double batteryVoltageMax) { this.batteryVoltageMax = batteryVoltageMax; }
    public Double getConsumptionScale() { return consumptionScale; }
    public void setConsumptionScale(Double consumptionScale) { this.consumptionScale = consumptionScale; }
    public Double getHarvestedScale() { return harvestedScale; }
    public void setHarvestedScale(Double harvestedScale) { this.harvestedScale = harvestedScale; }
    public Double getTemperatureScale() { return temperatureScale; }
    public void setTemperatureScale(Double temperatureScale) { this.temperatureScale = temperatureScale; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
