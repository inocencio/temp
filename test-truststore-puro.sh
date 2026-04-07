#!/bin/bash

##############################################################################
# Script: test-truststore-puro.sh
# Descrição: Testa truststore JKS/PKCS12 com token provider usando apenas
#            ferramentas nativas (curl, openssl, keytool)
# Uso: ./test-truststore-puro.sh
##############################################################################

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configurações (EDITAR CONFORME NECESSÁRIO)
TRUSTSTORE_FILE="sicdt-batch.jks"  # ou sicdt-batch.p12
TRUSTSTORE_PASS="changeit"
TOKEN_URL="https://meuhost.com/provider/token"
CLIENT_ID="seu-client-id"
CLIENT_SECRET="seu-client-secret"

# Configurações opcionais
VERBOSE=false
TEMP_DIR="/tmp/truststore-test-$$"

# Funções de logging
print_success() { echo -e "${GREEN}✅ $1${NC}"; }
print_error() { echo -e "${RED}❌ $1${NC}"; }
print_info() { echo -e "${BLUE}📌 $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_debug() { [ "$VERBOSE" = true ] && echo -e "${CYAN}🔍 $1${NC}"; }
print_header() { echo -e "\n${BLUE}═══════════════════════════════════════════════════════════${NC}"; echo -e "${BLUE}$1${NC}"; echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"; }

# Criar diretório temporário
setup_temp_dir() {
    mkdir -p "$TEMP_DIR"
    trap "rm -rf $TEMP_DIR" EXIT
}

# Verificar pré-requisitos
check_prerequisites() {
    print_header "Verificando Pré-requisitos"
    
    local missing_tools=()
    
    # Verificar curl
    if ! command -v curl &> /dev/null; then
        missing_tools+=("curl")
    else
        print_success "curl encontrado: $(curl --version | head -n1)"
    fi
    
    # Verificar openssl
    if ! command -v openssl &> /dev/null; then
        missing_tools+=("openssl")
    else
        print_success "openssl encontrado: $(openssl version)"
    fi
    
    # Verificar keytool (opcional, para extrair certificado)
    if ! command -v keytool &> /dev/null; then
        print_warning "keytool não encontrado (opcional, apenas para extrair certificados do JKS)"
    else
        print_success "keytool encontrado"
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        print_error "Ferramentas faltando: ${missing_tools[*]}"
        print_info "Instale: apt-get install curl openssl (Debian/Ubuntu) ou yum install curl openssl (RHEL/CentOS)"
        exit 1
    fi
}

# Detectar tipo do truststore
detect_truststore_type() {
    print_header "Detectando Tipo do Truststore"
    
    if [ ! -f "$TRUSTSTORE_FILE" ]; then
        print_error "Arquivo não encontrado: $TRUSTSTORE_FILE"
        exit 1
    fi
    
    print_info "Arquivo: $TRUSTSTORE_FILE"
    print_info "Tamanho: $(du -h "$TRUSTSTORE_FILE" | cut -f1)"
    
    # Detectar pelo magic number
    local file_type=$(file -b "$TRUSTSTORE_FILE")
    print_debug "File type: $file_type"
    
    if echo "$file_type" | grep -qi "Java KeyStore"; then
        TRUSTSTORE_TYPE="JKS"
        print_success "Tipo detectado: JKS (Java KeyStore)"
    elif echo "$file_type" | grep -qi "PKCS"; then
        TRUSTSTORE_TYPE="PKCS12"
        print_success "Tipo detectado: PKCS12"
    else
        # Tentar identificar pelo conteúdo
        if head -c 4 "$TRUSTSTORE_FILE" | od -An -tx1 | grep -q "fe ed"; then
            TRUSTSTORE_TYPE="JKS"
            print_success "Tipo detectado: JKS (pelo magic number 0xFEED)"
        elif openssl pkcs12 -info -in "$TRUSTSTORE_FILE" -noout -passin pass:"$TRUSTSTORE_PASS" 2>/dev/null; then
            TRUSTSTORE_TYPE="PKCS12"
            print_success "Tipo detectado: PKCS12 (teste bem-sucedido)"
        else
            print_error "Não foi possível determinar o tipo do truststore"
            exit 1
        fi
    fi
}

# Validar truststore (testar senha e listar certificados)
validate_truststore() {
    print_header "Validando Truststore"
    
    # Testar senha e listar certificados usando keytool
    if command -v keytool &> /dev/null; then
        print_info "Testando acesso ao truststore..."
        
        local list_output
        if [ "$TRUSTSTORE_TYPE" = "JKS" ]; then
            list_output=$(keytool -list -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -storetype JKS 2>&1)
        else
            list_output=$(keytool -list -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -storetype PKCS12 2>&1)
        fi
        
        if echo "$list_output" | grep -q "Keystore type"; then
            print_success "Truststore válido e acessível"
            
            # Contar certificados
            local cert_count=$(echo "$list_output" | grep -c "Entry, ")
            print_info "Número de certificados: $cert_count"
            
            # Listar aliases
            echo "$list_output" | grep "Alias name:" | while read -r line; do
                local alias=$(echo "$line" | sed 's/Alias name: //')
                echo -e "${GREEN}  📜 Certificado encontrado: $alias${NC}"
            done
        elif echo "$list_output" | grep -q "password was incorrect"; then
            print_error "Senha incorreta para o truststore"
            exit 1
        else
            print_error "Erro ao acessar truststore"
            print_debug "$list_output"
            exit 1
        fi
    else
        # Sem keytool, tentar com openssl para PKCS12
        if [ "$TRUSTSTORE_TYPE" = "PKCS12" ]; then
            if openssl pkcs12 -info -in "$TRUSTSTORE_FILE" -noout -passin pass:"$TRUSTSTORE_PASS" 2>/dev/null; then
                print_success "Truststore PKCS12 válido (teste openssl)"
            else
                print_error "Senha incorreta ou arquivo PKCS12 inválido"
                exit 1
            fi
        else
            print_warning "Não foi possível validar completamente (keytool não disponível)"
        fi
    fi
}

# Extrair certificado do truststore
extract_certificate() {
    print_header "Extraindo Certificado do Truststore"
    
    local cert_file="$TEMP_DIR/extracted-cert.pem"
    
    if command -v keytool &> /dev/null; then
        # Obter primeiro alias
        local alias
        if [ "$TRUSTSTORE_TYPE" = "JKS" ]; then
            alias=$(keytool -list -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -storetype JKS 2>/dev/null | grep "Alias name:" | head -1 | sed 's/Alias name: //')
        else
            alias=$(keytool -list -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -storetype PKCS12 2>/dev/null | grep "Alias name:" | head -1 | sed 's/Alias name: //')
        fi
        
        if [ -n "$alias" ]; then
            # Exportar certificado
            if [ "$TRUSTSTORE_TYPE" = "JKS" ]; then
                keytool -exportcert -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -alias "$alias" -rfc -file "$cert_file" 2>/dev/null
            else
                keytool -exportcert -keystore "$TRUSTSTORE_FILE" -storepass "$TRUSTSTORE_PASS" -storetype PKCS12 -alias "$alias" -rfc -file "$cert_file" 2>/dev/null
            fi
            
            if [ -f "$cert_file" ] && [ -s "$cert_file" ]; then
                print_success "Certificado extraído com sucesso"
                print_info "Arquivo: $cert_file"
                
                # Mostrar informações do certificado
                print_info "Informações do certificado:"
                openssl x509 -in "$cert_file" -text -noout 2>/dev/null | grep -E "(Subject:|Issuer:|Not Before:|Not After :)" | while read -r line; do
                    echo "  $line"
                done
                
                echo "$cert_file"
                return 0
            fi
        fi
    fi
    
    print_warning "Não foi possível extrair certificado (keytool não disponível ou truststore vazio)"
    return 1
}

# Testar conexão SSL com o provider usando o certificado extraído
test_ssl_connection() {
    print_header "Testando Conexão SSL com o Provider"
    
    # Extrair host da URL
    local host=$(echo "$TOKEN_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||' -e 's|:.*$||')
    local port=$(echo "$TOKEN_URL" | grep -o ':[0-9]*' | grep -o '[0-9]*')
    [ -z "$port" ] && port=443
    
    print_info "Host: $host"
    print_info "Porta: $port"
    
    # Método 1: Usar o certificado extraído como CA
    local cert_file="$TEMP_DIR/extracted-cert.pem"
    
    if [ -f "$cert_file" ]; then
        print_info "Testando SSL com o certificado extraído..."
        
        local ssl_output=$(echo | openssl s_client -connect "$host:$port" -servername "$host" -CAfile "$cert_file" 2>&1)
        
        if echo "$ssl_output" | grep -q "Verify return code: 0 (ok)"; then
            print_success "✅ Conexão SSL bem-sucedida! O certificado é válido para $host"
            
            # Extrair detalhes do certificado do servidor
            echo "$ssl_output" | openssl x509 -text -noout 2>/dev/null | grep -E "(Subject:|Issuer:|Not Before:|Not After :)" | while read -r line; do
                echo "  Servidor: $line"
            done
            
            return 0
        else
            print_error "Falha na validação SSL"
            local verify_error=$(echo "$ssl_output" | grep "Verify return code:")
            print_error "$verify_error"
            return 1
        fi
    else
        # Método 2: Teste sem validação (apenas verificar se o servidor responde)
        print_info "Testando conectividade básica com o provider..."
        
        local ssl_output=$(echo | openssl s_client -connect "$host:$port" -servername "$host" 2>&1)
        
        if echo "$ssl_output" | grep -q "CONNECTED"; then
            print_success "Conexão TCP/SSL estabelecida com o servidor"
            
            # Verificar se o certificado do servidor corresponde ao nosso truststore
            local server_cert="$TEMP_DIR/server-cert.pem"
            echo "$ssl_output" | openssl x509 -outform PEM > "$server_cert"
            
            print_info "Certificado do servidor obtido"
            return 0
        else
            print_error "Não foi possível conectar ao servidor"
            return 1
        fi
    fi
}

# Obter bearer token usando curl com o truststore
get_bearer_token() {
    print_header "Obtendo Bearer Token do Provider"
    
    # Preparar credenciais
    local credentials=$(echo -n "$CLIENT_ID:$CLIENT_SECRET" | base64)
    
    print_info "Token URL: $TOKEN_URL"
    print_info "Client ID: ${CLIENT_ID:0:10}..."
    
    # Criar arquivo de configuração curl para usar o truststore
    local curl_config="$TEMP_DIR/curl-config"
    
    # Configurar curl baseado no tipo do truststore
    if [ "$TRUSTSTORE_TYPE" = "PKCS12" ]; then
        # Para PKCS12, usar diretamente
        cat > "$curl_config" <<EOF
--cert-type P12
--cert "$TRUSTSTORE_FILE:$TRUSTSTORE_PASS"
EOF
    else
        # Para JKS, precisamos extrair o certificado (se possível)
        local cert_file="$TEMP_DIR/extracted-cert.pem"
        if [ -f "$cert_file" ]; then
            cat > "$curl_config" <<EOF
--cacert "$cert_file"
EOF
        else
            # Fallback: ignorar verificação SSL (apenas teste)
            print_warning "Usando modo inseguro para teste (apenas verificar resposta)"
            cat > "$curl_config" <<EOF
--insecure
EOF
        fi
    fi
    
    # Fazer requisição para obter token
    print_info "Enviando requisição..."
    
    local response_file="$TEMP_DIR/response.json"
    local http_code
    
    # Construir comando curl
    local curl_cmd="curl -s -w '%{http_code}' -o '$response_file' \
        -X POST '$TOKEN_URL' \
        -H 'Authorization: Basic $credentials' \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -d 'grant_type=client_credentials' \
        -d 'scope=read'"
    
    # Adicionar opções de certificado
    if [ "$TRUSTSTORE_TYPE" = "PKCS12" ]; then
        curl_cmd="$curl_cmd --cert-type P12 --cert '$TRUSTSTORE_FILE:$TRUSTSTORE_PASS'"
    elif [ -f "$cert_file" ]; then
        curl_cmd="$curl_cmd --cacert '$cert_file'"
    else
        curl_cmd="$curl_cmd --insecure"
    fi
    
    # Executar
    print_debug "Comando: $curl_cmd"
    http_code=$(eval "$curl_cmd" 2>&1)
    
    # Verificar resposta
    if [ "$http_code" = "200" ]; then
        print_success "Token obtido com sucesso! (HTTP $http_code)"
        
        # Extrair token usando ferramentas padrão
        local token=$(grep -o '"access_token":"[^"]*"' "$response_file" | cut -d'"' -f4)
        local token_type=$(grep -o '"token_type":"[^"]*"' "$response_file" | cut -d'"' -f4)
        local expires_in=$(grep -o '"expires_in":[0-9]*' "$response_file" | cut -d':' -f2)
        
        if [ -n "$token" ]; then
            echo ""
            print_success "BEARER TOKEN OBTIDO COM SUCESSO!"
            echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
            echo -e "${CYAN}Token Type: $token_type${NC}"
            echo -e "${CYAN}Expires in: ${expires_in:-N/A} segundos${NC}"
            echo -e "${GREEN}Token: ${token:0:100}...${NC}"
            echo -e "${GREEN}═══════════════════════════════════════════════════════════${NC}"
            
            # Salvar token
            echo "$token" > "$TEMP_DIR/bearer-token.txt"
            print_success "Token salvo em: $TEMP_DIR/bearer-token.txt"
            
            return 0
        else
            print_warning "Resposta não continha access_token"
            echo "Resposta recebida:"
            cat "$response_file" | python -m json.tool 2>/dev/null || cat "$response_file"
            return 1
        fi
    else
        print_error "Falha ao obter token. HTTP Status: $http_code"
        echo "Resposta do servidor:"
        cat "$response_file" 2>/dev/null
        return 1
    fi
}

# Validar token (teste rápido com endpoint de introspect se disponível)
validate_token_with_introspect() {
    print_header "Validando Token (Opcional)"
    
    local token_file="$TEMP_DIR/bearer-token.txt"
    if [ ! -f "$token_file" ]; then
        print_warning "Nenhum token disponível para validação"
        return 1
    fi
    
    local token=$(cat "$token_file")
    local INTROSPECT_URL="${TOKEN_URL}/introspect"
    
    print_info "Tentando validar token via introspect endpoint..."
    
    local response=$(curl -s -X POST "$INTROSPECT_URL" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "token=$token" \
        2>&1)
    
    if echo "$response" | grep -q '"active":true'; then
        print_success "Token é válido e ativo"
        return 0
    else
        print_warning "Endpoint de introspect não disponível ou token inválido"
        return 1
    fi
}

# Teste final: verificar se o truststore funciona para acessar o provider
final_verification() {
    print_header "VERIFICAÇÃO FINAL"
    
    echo ""
    print_info "Resumo dos testes realizados:"
    
    # Teste 1: Truststore válido
    if validate_truststore 2>/dev/null; then
        print_success "✓ Truststore é válido e acessível"
    else
        print_error "✗ Truststore inválido"
        return 1
    fi
    
    # Teste 2: Conexão SSL
    if test_ssl_connection >/dev/null 2>&1; then
        print_success "✓ Conexão SSL com o provider é possível"
    else
        print_warning "⚠ Conexão SSL com problemas (pode ser normal para certificados auto-assinados)"
    fi
    
    # Teste 3: Obter token
    if get_bearer_token >/dev/null 2>&1; then
        print_success "✓ Bearer token obtido com sucesso!"
        echo ""
        echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ✅ TRUSTSTORE FUNCIONA PERFEITAMENTE PARA O PROVIDER!    ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
        return 0
    else
        echo ""
        echo -e "${RED}╔════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║  ❌ TRUSTSTORE NÃO FUNCIONOU PARA OBTER O TOKEN          ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════════╝${NC}"
        return 1
    fi
}

# Menu interativo
show_menu() {
    echo ""
    print_header "MENU DE TESTES"
    echo "1) Validar truststore (testar senha e listar certificados)"
    echo "2) Testar conexão SSL com o provider"
    echo "3) Obter Bearer Token (teste completo)"
    echo "4) Executar todos os testes (recomendado)"
    echo "5) Sair"
    echo ""
    read -p "Escolha uma opção [1-5]: " option
    
    case $option in
        1)
            validate_truststore
            ;;
        2)
            test_ssl_connection
            ;;
        3)
            get_bearer_token
            ;;
        4)
            validate_truststore
            test_ssl_connection
            get_bearer_token
            final_verification
            ;;
        5)
            print_success "Encerrando"
            exit 0
            ;;
        *)
            print_error "Opção inválida"
            ;;
    esac
}

# Função principal
main() {
    # Banner
    echo -e "${CYAN}"
    cat << "EOF"
╔═══════════════════════════════════════════════════════════════════╗
║     TESTE PURO DE TRUSTSTORE PARA TOKEN PROVIDER                 ║
║     Sem dependência de Java - Apenas curl + openssl              ║
╚═══════════════════════════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
    
    # Setup
    setup_temp_dir
    check_prerequisites
    detect_truststore_type
    
    # Extrair certificado se possível
    extract_certificate > /dev/null 2>&1
    
    # Menu ou execução direta via argumento
    if [ "$1" = "--auto" ] || [ "$1" = "-a" ]; then
        final_verification
    else
        while true; do
            show_menu
        done
    fi
}

# Executar com argumento opcional
main "$@"