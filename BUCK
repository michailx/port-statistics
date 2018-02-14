COMPILE_DEPS = [
    '//lib:CORE_DEPS',
    '//lib:JACKSON',
    '//lib:METRICS',
    '//lib:org.apache.karaf.shell.console',
    '//lib:metrics-influxdb',
    '//lib:influxdb-java',
    '//lib:commons-codec',
    '//lib:retrofit',
    '//lib:okhttp',
    '//lib:okio',
    '//lib:gson',
    '//cli:onos-cli',
]

EXCLUDED_BUNDLES = [
    '//lib:metrics-influxdb',
    '//lib:influxdb-java',
    '//lib:commons-codec',
    '//lib:retrofit',
    '//lib:okhttp',
    '//lib:gson',
    '//lib:okio',
]

osgi_jar (
    deps = COMPILE_DEPS,
)

onos_app (
    title = 'Port Statistics',
    category = 'Device Monitoring',
    url = 'http://onosproject.org',
    description = 'Port Statistics for device monitoring.',
    required_apps = [ ],
    excluded_bundles = EXCLUDED_BUNDLES,
)
