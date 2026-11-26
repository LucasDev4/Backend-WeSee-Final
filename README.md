# Backend-atlas
B A C K E N D - Lucas Rodrigues Ferraz

API do WeSee (Spring Boot 3.5 / Java 21 / MySQL / JWT / Flyway / Swagger).

## Rodando
```
# MySQL (o banco "atlas" é criado sozinho). Variáveis opcionais:
#   DB_USERNAME  DB_PASSWORD  JWT_SECRET  VINCULO_CODE_SECRET  ATLAS_AI_BASE_URL
./mvnw spring-boot:run          # http://0.0.0.0:8080   |   Swagger: /swagger-ui/index.html
./mvnw test                     # testes unitários (não precisam de banco); contextLoads precisa do MySQL
```
> **Segurança:** o `application.properties` tem senha de banco e `jwt.secret` padrão de desenvolvimento. Em qualquer ambiente real use as
> variáveis `DB_PASSWORD`, `JWT_SECRET` e `VINCULO_CODE_SECRET` (e troque a senha que estiver versionada).

## Contrato já existente (preservado, sem alterações de rota ou corpo)
| Método | Rota | Auth | |
|---|---|---|---|
| POST | `/auth/register` | não | `{nome, email, senha(6 números), cpf?, telefone?, tipo?}` → `201 {id,nome,cpf,email,telefone,tipo}` |
| POST | `/auth/login` | não | `{email, senha}` → `{token, nome, email, tipo}` |
| GET | `/user/me` | JWT | dados do usuário logado |
| POST | `/detect` | JWT | IA (imagem → detecções) |

`tipo` = `PCD_VISUAL` (padrão) ou `RESPONSAVEL`. **Cadastro e login não dependem de nenhum vínculo**: um PCD usa o app sem responsável
e um responsável usa o app sem PCD, indefinidamente.

---

# Vínculo opcional PCD ⇄ Responsável

## Diagnóstico do projeto (o que foi reaproveitado)
- Entidade `User` já tinha `tipo`, `ativo`, `senha` (BCrypt), `role`; auth = Spring Security + JWT (`JwtService`, `JwtAuthenticationFilter`).
- Erros: `GlobalExceptionHandler` (formato `{timestamp,status,error,message,fields?}`) — estendido, não duplicado.
- Banco: Flyway (V1–V3) com `ddl-auto=validate` — o esquema novo veio na migration **V4**; nada existente foi alterado.
- Swagger já com Bearer JWT — mantido; os novos endpoints foram documentados com `@Tag/@Operation/@ApiResponses`.
- **Nenhum** segundo sistema de auth/tokens/usuários foi criado. O tipo da conta é lido do **banco** (usuário carregado pelo JWT), nunca do cliente.

## Arquivos
**Criados** — `entity/`: `CodigoVinculacao`, `VinculoResponsavel`, `TentativaVinculacao`, `Permissao`, `StatusVinculo`, `StatusCodigo`, `TipoUsuario`, `PermissaoSetConverter` ·
`repository/`: `CodigoVinculacaoRepository`, `VinculoRepository`, `TentativaVinculacaoRepository` ·
`service/`: `CodigoVinculacaoService`, `VinculoService`, `AutorizacaoVinculoService`, `TentativaVinculacaoService`, `VinculoRegras` ·
`controller/`: `VinculoController`, `ResponsavelDadosController` ·
`dto/`: `GerarCodigoRequest/Response`, `SolicitarVinculoRequest`, `AtualizarPermissoesRequest`, `VinculoResponse`, `VinculosResponse`, `DadosPcdResponse` ·
`exception/VinculoException` · `config/`: `ClockConfig`, `VinculoProperties` · `db/migration/V4__create_vinculos.sql` ·
testes em `src/test/.../service|security|exception|support`.

**Alterados (mínimo):** `GlobalExceptionHandler` (+1 handler), `JwtAuthenticationFilter` (conta **desativada** não autentica mais, mesmo com token válido — revogação imediata),
`application.properties` (+ bloco `vinculo.*`).

## Migration V4 (aplica em banco existente, sem destruir dados)
- `codigo_vinculacao` — só o **HMAC-SHA256** do código (`codigo_hash` único), PCD dono, status, permissões, criado/expira/utilizado/revogado.
- `vinculo_responsavel` — FKs para `users`, status, permissões, `codigo_id` (consentimento), `criado_por`, datas, `revogado_por`.
  Coluna gerada `chave_ativa` + índice único ⇒ **impossível ter dois vínculos ATIVO para o mesmo par**, mesmo com requisições simultâneas
  (vínculos revogados ficam no histórico e permitem vincular de novo). `CHECK (responsavel_id <> pcd_id)`.
- `tentativa_vinculacao` — tentativas falhas (limite anti força bruta).

## Fluxo (decisão de consentimento documentada)
1. **PCD** autenticado: `POST /api/vinculos/codigos` → recebe o código (6 dígitos, uso único, validade 300 s). Pode escolher o que compartilhar; sem corpo = `LOCALIZACAO, STATUS_DISPOSITIVO, ALERTAS` (histórico de rotas é opt-in). Gerar outro código cancela os anteriores. Limite: 5 códigos / 10 min (429).
2. O PCD compartilha o código **voluntariamente** (ex.: lendo em voz alta).
3. **Responsável** autenticado: `POST /api/vinculos/solicitar {codigo}`. Valida formato, existência (HMAC), validade, estado, tipo da conta e duplicidade; consome o código com **um único UPDATE condicional** (atômico) e cria o vínculo — tudo numa transação (se algo falha, o código **não** é consumido).
4. **Ativação imediata:** como o PCD gerou o código de propósito, usá-lo *é* o consentimento; por isso **não há etapa de aprovação extra** nem `/autorizar` (o status `PENDENTE` fica reservado para um futuro fluxo iniciado pelo responsável). O consentimento fica registrado: `vinculo.codigo_id` → quando o PCD gerou, para quem, até quando valia, quais permissões; + `autorizado_em`.
5. **Revogação:** `DELETE /api/vinculos/{id}` (qualquer participante) → `REVOGADO` + data/autor. Nenhuma conta é apagada.
6. **Permissões:** `PUT /api/vinculos/{id}/permissoes` (**só o PCD**). Vale na hora, sem novo login. O responsável nunca altera as próprias permissões.

> O código é uma **autorização de vinculação**, não prova de parentesco ou tutela legal.

## Endpoints novos
| Método | Rota | Quem | Resposta |
|---|---|---|---|
| POST | `/api/vinculos/codigos` | PCD_VISUAL | `201 {codigo, expiraEm, validadeSegundos, permissoes}` |
| POST | `/api/vinculos/solicitar` | RESPONSAVEL | `201 VinculoResponse` (status `ATIVO`) |
| GET | `/api/vinculos[?incluirRevogados=true]` | qualquer logado | `{vinculos:[...]}` — o "outro lado" (nome, tipo, id) |
| GET | `/api/vinculos/{id}` | participante | `VinculoResponse` (terceiros: 404) |
| DELETE | `/api/vinculos/{id}` | participante | `204` |
| PUT | `/api/vinculos/{id}/permissoes` | PCD do vínculo | `VinculoResponse` |
| GET | `/api/responsavel/pcds/{pcdId}/localizacao` | RESPONSAVEL vinculado + `LOCALIZACAO` | `DadosPcdResponse` |
| GET | `/api/responsavel/pcds/{pcdId}/status` | idem + `STATUS_DISPOSITIVO` | idem |
| GET | `/api/responsavel/pcds/{pcdId}/alertas` | idem + `ALERTAS` | idem |
| GET | `/api/responsavel/pcds/{pcdId}/historico` | idem + `HISTORICO_ROTAS` | idem |

Exemplos (`$T_PCD`/`$T_RESP` = tokens de `/auth/login`):
```bash
# 1) PCD gera o código (corpo opcional)
curl -X POST localhost:8080/api/vinculos/codigos -H "Authorization: Bearer $T_PCD" -H "Content-Type: application/json" \
     -d '{"permissoes":["LOCALIZACAO","ALERTAS"]}'
#  -> {"codigo":"482731","expiraEm":"...","validadeSegundos":300,"permissoes":["LOCALIZACAO","ALERTAS"]}

# 2) Responsável usa o código
curl -X POST localhost:8080/api/vinculos/solicitar -H "Authorization: Bearer $T_RESP" -H "Content-Type: application/json" \
     -d '{"codigo":"482 731"}'
#  -> {"id":15,"relacionadoId":1,"nomeRelacionado":"...","tipoRelacionado":"PCD_VISUAL","status":"ATIVO","permissoes":[...],...}

# 3) Responsável consulta (use o relacionadoId como pcdId)
curl localhost:8080/api/responsavel/pcds/1/localizacao -H "Authorization: Bearer $T_RESP"
#  -> {"pcdId":1,"categoria":"LOCALIZACAO","disponivel":false,"mensagem":"Dado ainda não disponível: ...","dados":null}

# 4) PCD retira o histórico / encerra o vínculo
curl -X PUT localhost:8080/api/vinculos/15/permissoes -H "Authorization: Bearer $T_PCD" -H "Content-Type: application/json" -d '{"permissoes":["ALERTAS"]}'
curl -X DELETE localhost:8080/api/vinculos/15 -H "Authorization: Bearer $T_PCD"
```

## Segurança (resumo)
- **Código:** `SecureRandom`, 6 dígitos (1 milhão de combinações, como no design do app; para mais segurança suba `vinculo.codigo-tamanho` para 8 **e** `VINCULO_CODE_LENGTH` no app), uso único, expira em 5 min, nunca em texto puro no banco **nem em logs**: guarda-se `HMAC-SHA256(código, VINCULO_CODE_SECRET)` — um SHA-256 simples de 8 dígitos seria quebrado em segundos se o banco vazasse.
- **Força bruta:** 5 tentativas falhas por usuário em 15 min → `429` (falhas gravadas em transação própria `REQUIRES_NEW`, sobrevivem ao rollback). Geração limitada a 5 códigos / 10 min.
- **Identidade sempre do JWT** (`@AuthenticationPrincipal`); nenhum endpoint aceita "id do PCD" como autoridade. O responsável só informa o *código*.
- **Autorização central** (`AutorizacaoVinculoService.exigirAcesso`): vínculo ATIVO + permissão, checados **a cada requisição** ⇒ revogar vínculo/permissão bloqueia na hora. Sem vínculo, PCD inexistente e id trocado na URL dão a **mesma** resposta `403 "Acesso negado."` (não dá para descobrir contas). Vínculos de terceiros: `404`.
- **Erros:** `400` inválido/expirado/cancelado · `401` sem token · `403` tipo errado/sem acesso · `404` vínculo inexistente/alheio · `409` código já usado / vínculo duplicado · `429` excesso de tentativas. Sem stack trace, SQL ou segredo.
- Mensagens distintas para código inválido/expirado/usado são um compromisso consciente: só quem possui o código as vê, e todas contam como tentativa falha.
- Não há endpoint para trocar o `tipo` da conta depois do cadastro.

## Testes (`./mvnw test`)
Unitários com JUnit 5 + Mockito + AssertJ (sem banco): geração (PCD ok, responsável/inativo recusados, 429, hash ≠ código, cancela anteriores), uso do código
(válido, inválido, expirado, usado, revogado, bloqueio por tentativas, mesmo tipo, duplicado, **consumo atômico perdido**, **duas threads → 1 sucesso**, vários responsáveis por PCD e vários PCDs por responsável),
autorização (vinculado ok, não vinculado nega as 4 categorias, id trocado, vínculo revogado, permissão retirada, PCD em rota de responsável, PCD desativado),
listagem/consulta/revogação (terceiro = 404, conta não é apagada, só o PCD altera permissões), cadastro/login independentes de vínculo, filtro JWT (token expirado e conta desativada) e mapeamento de erros.

**Não coberto por testes automáticos:** concorrência *real no MySQL* (índice único `uk_vinculo_ativo` e UPDATE condicional) e a subida do contexto com as migrations — exigem MySQL/Testcontainers (não há essa dependência no `pom.xml`; não foi adicionada sem necessidade). Para validar: suba o app (`contextLoads`/Flyway) e dispare duas chamadas simultâneas de `/api/vinculos/solicitar` com o mesmo código.

## Como testar os dois tipos de usuário (Swagger ou curl)
1. Registre `ana@t.com` (`"tipo":"PCD_VISUAL"`) e `bruno@t.com` (`"tipo":"RESPONSAVEL"`) com senha de 6 números; faça login nos dois (cada um funciona sozinho, sem vínculo).
2. Ana gera o código → Bruno o usa → `GET /api/vinculos` lista um vínculo para cada.
3. Bruno chama `/api/responsavel/pcds/{id da Ana}/localizacao` → `200` (`disponivel:false` até a Ana publicar a posição; depois `disponivel:true`). Troque o id por outro → `403`.
4. Ana tira `LOCALIZACAO` em `PUT /permissoes` → Bruno recebe `403` imediatamente. Ana revoga o vínculo → `403` em tudo.
5. Bruno tentando `POST /api/vinculos/codigos` → `403`; Ana tentando `/solicitar` → `403`.

## Pendências (dependem de funcionalidades ainda não existentes)
- **Localização** já é coletada (`POST /api/pcd/localizacao`, veja "Endereços, rotas e localização" abaixo). **Status do dispositivo, alertas e histórico de rotas** ainda não são coletados nem gravados: as rotas já **autorizam** corretamente, mas respondem `disponivel:false` (sem inventar dados). Quando houver coleta/persistência, basta preencher o corpo em `ResponsavelDadosController`.
- O app Android (modais de vínculo) já usa o código de **6 dígitos**. Com 6 dígitos o espaço de códigos é pequeno: a proteção vem da expiração (5 min), do uso único, do limite de tentativas (5 falhas/15 min) e do HMAC; como o índice único do hash vale para sempre, muitos milhares de códigos gerados ao longo do tempo aumentam as colisões (o serviço sorteia outro) — em escala maior, use 8 dígitos.
- Fluxo iniciado pelo responsável (com aprovação do PCD) e limpeza periódica de códigos/tentativas antigos ficam para uma próxima etapa (`PENDENTE` já reservado).

## Teste de integração com o app (`scripts/smoke_test.py`)
Com o MySQL e o backend rodando: `python scripts/smoke_test.py` (ou `python scripts/smoke_test.py http://IP-DO-PC:8080`).
Faz, pela rede, o mesmo que o app WeSee: cadastro/login dos dois tipos, `/user/me`, geração e uso do código de 6 dígitos, listas de vínculo,
permissões, bloqueio por id trocado e revogação (35 verificações). Só usa a biblioteca padrão do Python 3 e cria contas descartáveis.
Se tudo passar, o back-end está pronto para o app; se algo falhar, a linha `FALHA` mostra o que o servidor respondeu.
O servidor sobe em `0.0.0.0:8080`, então um celular na mesma rede acessa por `http://IP-DO-PC:8080` (libere a porta no firewall).

## Câmera + IA (como tudo se conecta)
`app Android (câmera) → POST /detect (JWT, multipart "image") → Backend-atlas → IA (WeSeeIa, FastAPI+YOLO) → backend → app (fala)`
1. Suba a IA (`WeSeeIa/iniciar_ia.bat`, porta 8000). O backend a encontra em `ATLAS_AI_BASE_URL` (padrão `http://localhost:8000`).
2. O backend filtra por `ATLAS_MIN_CONFIDENCE` (padrão **0.35**) e devolve, para cada quadro: `objects[]` (`class`, `label` em português, `position`, `proximity`, `priority`),
   `guidance` (alerta do objeto mais urgente, ex.: "Pessoa muito próxima à sua frente") e `scene` (**tudo** que a câmera vê, ex.: "Vejo duas pessoas à sua frente e um carro muito próximo à sua direita.").
3. Vocabulário em português das **25 classes** do modelo em `PriorityRules` (rua e prédio entram só na descrição, nunca como alerta). Teste: `GuidanceSceneTest`.
4. Teste ponta a ponta com a IA ligada: `python scripts/smoke_test.py --ia`.

## Publicado no Render
Variáveis de ambiente do serviço: `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (conforme o seu `application.properties`), `JWT_SECRET`, `VINCULO_CODE_SECRET`
e **`ATLAS_AI_BASE_URL`** (endereço público da IA; sem ela `/detect` responde 503 e o app mostra "A IA está indisponível agora").
O app (padrão) fala com `https://backend-wesee-final.onrender.com/`. Confira com `python scripts/smoke_test.py https://backend-wesee-final.onrender.com`
(no plano gratuito a 1ª chamada pode levar ~1 min para o serviço acordar).


## Endereços, rotas e localização (Nominatim + OSRM)
`app Android → backend (JWT) → Nominatim / OSRM → backend → app → Google Maps SDK (só desenha)`. O app **não** conhece os servidores públicos.

| Endpoint (todos exigem JWT) | O que faz |
|---|---|
| `GET /api/geocoding/search?q=...[&lat=&lon=][&limit=]` | Nominatim `/search`. `lat`/`lon` (juntos) só dão preferência a resultados próximos. Dispare só quando o usuário **confirmar** a busca. |
| `GET /api/geocoding/reverse?lat=&lon=` | Nominatim `/reverse`: endereço aproximado (`404` se não houver). |
| `GET /api/routes?originLat=&originLon=&destinationLat=&destinationLon=` | OSRM `/route`: `distanceMeters`, `durationSeconds`, `coordinates[]` (**latitude/longitude**), `steps[]`, `profile`, `walking`, `warning`, `safetyNotice`. |
| `POST /api/pcd/localizacao` · `DELETE /api/pcd/localizacao` | Só `PCD_VISUAL`: publica/apaga a própria última posição. Só grava se houver vínculo ATIVO com permissão `LOCALIZACAO`. |
| `GET /api/responsavel/pcds/{pcdId}/localizacao` | (já existia) agora devolve a posição (`dados.latitude/longitude/precisaoMetros/atualizadoEm/idadeSegundos`) depois de `exigirAcesso`. |

**Rota a pé — leia isto.** O `router.project-osrm.org` só tem o grafo de **carro** e **ignora** `foot`/`bike` na URL (devolve a rota de carro sem erro). Por isso:
- o padrão aponta para a instância de pedestres do FOSSGIS (`routing.openstreetmap.de/routed-foot/...`), mas as rotas a pé ficam **desligadas** (`503`) até `OSRM_WALKING_CONFIRMED=true`;
- confirme com `python scripts/verify_osrm_profile.py` (compara com a rota de carro; precisa de internet);
- se `OSRM_WALKING_CONFIRMED=true` for combinado com host `router.project-osrm.org`, o backend **ignora** a confirmação e continua bloqueando;
- `OSRM_ALLOW_NON_WALKING_DEMO=true` (só dev) devolve a rota com `walking=false` + aviso.
Os servidores públicos são demonstração (sem garantia de disponibilidade; uso não comercial, 1 req/s). Para produção, rode um OSRM próprio com o perfil `foot` e use `OSRM_BASE_URL=https://seu-osrm` + `OSRM_ROUTE_PATH=/route/v1/foot`.

**Variáveis de ambiente (todas opcionais; nomes reais em `application.properties`):** `NOMINATIM_BASE_URL`, `NOMINATIM_CONTACT_EMAIL` (**defina**: a política pede contato), `NOMINATIM_USER_AGENT`, `NOMINATIM_COUNTRY_CODES` (padrão `br`), `NOMINATIM_LANGUAGE`, `NOMINATIM_MIN_INTERVAL_MS`, `NOMINATIM_MAX_QUEUE_WAIT_MS`, `NOMINATIM_CONNECT_TIMEOUT_MS`, `NOMINATIM_READ_TIMEOUT_MS`, `NOMINATIM_SEARCH_CACHE_TTL_SECONDS`, `NOMINATIM_REVERSE_CACHE_TTL_SECONDS`, `NOMINATIM_CACHE_MAX_ENTRIES`, `OSRM_BASE_URL`, `OSRM_ROUTE_PATH`, `OSRM_PROFILE_LABEL`, `OSRM_WALKING_CONFIRMED`, `OSRM_ALLOW_NON_WALKING_DEMO`, `OSRM_MIN_INTERVAL_MS`, `OSRM_MAX_QUEUE_WAIT_MS`, `OSRM_CONNECT_TIMEOUT_MS`, `OSRM_READ_TIMEOUT_MS`, `GEO_ATTRIBUTION`.

**Controle de uso:** 1 chamada/1,1 s por provedor para a aplicação toda (excesso espera até 3 s; depois `429`), cache em memória (busca 6 h, reverso 24 h, chave do reverso arredondada a ~11 m), `User-Agent` identificável. Rotas **não** são cacheadas (dependem da posição exata da pessoa). Logs não contêm URL, texto buscado nem coordenadas.
**Erros:** `400` parâmetro inválido/ausente · `404` sem endereço/rota · `429` limite · `502` resposta inválida · `503` provedor fora do ar ou rota a pé não habilitada · `504` timeout.
**Privacidade da localização:** migration `V5` (tabela `localizacao_pcd`, 1 linha por PCD, sem histórico). O responsável só lê após `AutorizacaoVinculoService.exigirAcesso` (vínculo ATIVO + permissão `LOCALIZACAO`, a cada chamada); sem responsável autorizado, nada é gravado e a posição antiga é apagada. O horário é o do servidor.
**Atribuição:** o app exibe "Dados © colaboradores do OpenStreetMap" (campo `attribution`). A rota **não** garante calçada acessível, travessia segura nem ausência de obstáculos/obras; a IA de detecção continua sendo a camada de segurança.
