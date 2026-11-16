const ECOSENSE_LOCAL=/^(localhost|127\.0\.0\.1)$/.test(location.hostname)&&location.port!=='8080';
window.ECOSENSE_API_URL=ECOSENSE_LOCAL?'http://localhost:8080/api':location.origin+'/api';
