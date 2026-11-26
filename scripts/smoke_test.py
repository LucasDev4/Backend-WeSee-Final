#!/usr/bin/env python3
"""
Teste de integração ponta a ponta do Backend-atlas (o mesmo contrato que o app WeSee usa).
Uso (com o backend e o MySQL rodando):   python scripts/smoke_test.py [http://localhost:8080]
Só usa a biblioteca padrão do Python 3. Cria contas descartáveis (e-mails com data/hora). Sai com código 1 se algo falhar.
"""
import json
import sys
import time
import urllib.error
import urllib.request

_args = [a for a in sys.argv[1:] if not a.startswith("--")]
BASE = (_args[0] if _args else "http://localhost:8080").rstrip("/")
SUFIXO = str(int(time.time()))
SENHA = "123456"
TESTAR_IA = "--ia" in sys.argv   # python smoke_test.py [URL] --ia  (exige a IA em execução)
JPEG_MINIMO_B64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAA0JCgsKCA0LCgsODg0PEyAVExISEyccHhcgLikxMC4pLSwzOko+MzZGNywtQFdBRkxOUlNSMj5aYVpQYEpRUk//2wBDAQ4ODhMREyYVFSZPNS01T09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT09PT0//wAARCABAAEADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooA/9k="
resultados = []


def chamar(metodo, caminho, corpo=None, token=None):
    dados = json.dumps(corpo).encode() if corpo is not None else (b"" if metodo in ("POST", "PUT") else None)
    req = urllib.request.Request(BASE + caminho, data=dados, method=metodo)
    req.add_header("Accept", "application/json")
    if dados is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            texto = r.read().decode()
            return r.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto)
        except ValueError:
            return e.code, texto


def enviar_imagem(caminho, jpeg, token):
    """POST multipart (campo 'image'), como o app faz."""
    limite = "----wesee" + SUFIXO
    corpo = (f"--{limite}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"frame.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").encode() \
        + jpeg + f"\r\n--{limite}--\r\n".encode()
    req = urllib.request.Request(BASE + caminho, data=corpo, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={limite}")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto)
        except ValueError:
            return e.code, texto


def checar(nome, condicao, detalhe=""):
    resultados.append(bool(condicao))
    print(("  OK    " if condicao else "  FALHA ") + nome + ("" if condicao else f"   -> {detalhe}"))
    return condicao


def secao(titulo):
    print("\n" + titulo)


def cadastrar(nome, tipo, email):
    return chamar("POST", "/auth/register", {"nome": nome, "email": email, "senha": SENHA, "tipo": tipo})


def entrar(email, senha=SENHA):
    return chamar("POST", "/auth/login", {"email": email, "senha": senha})


def main():
    print(f"Backend: {BASE}")
    try:
        chamar("GET", "/v3/api-docs")
    except OSError as e:
        print(f"\nNÃO consegui falar com {BASE}: {e}\nConfira se o backend e o MySQL estão rodando e se a URL/porta estão certas.")
        return 1

    email_pcd, email_resp, email_resp2 = (f"smoke.{papel}.{SUFIXO}@teste.com" for papel in ("pcd", "resp", "resp2"))

    secao("1) Cadastro e login (independentes de vínculo)")
    st, b = cadastrar("Ana Teste", "PCD_VISUAL", email_pcd)
    checar("PCD cadastra sem responsável (201, tipo PCD_VISUAL)", st == 201 and b and b.get("tipo") == "PCD_VISUAL", f"{st} {b}")
    pcd_id = (b or {}).get("id")
    st, b = cadastrar("Bruno Teste", "RESPONSAVEL", email_resp)
    checar("Responsável cadastra sem PCD (201, tipo RESPONSAVEL)", st == 201 and b and b.get("tipo") == "RESPONSAVEL", f"{st} {b}")
    st, b = cadastrar("Carla Teste", "RESPONSAVEL", email_resp2)
    checar("Segundo responsável cadastra (201)", st == 201, f"{st} {b}")
    st, b = cadastrar("Ana Repetida", "PCD_VISUAL", email_pcd)
    checar("E-mail repetido é recusado (409)", st == 409, f"{st} {b}")
    st, b = chamar("POST", "/auth/register", {"nome": "Sem Senha Valida", "email": f"x.{SUFIXO}@teste.com", "senha": "12ab", "tipo": "PCD_VISUAL"})
    checar("Senha fora do padrão de 6 números é recusada (400 com 'fields')", st == 400 and isinstance(b, dict) and "fields" in b, f"{st} {b}")

    st, b = entrar(email_pcd)
    checar("Login do PCD devolve token e tipo", st == 200 and b and b.get("token") and b.get("tipo") == "PCD_VISUAL", f"{st} {b}")
    t_pcd = (b or {}).get("token")
    st, b = entrar(email_resp)
    checar("Login do responsável devolve token e tipo", st == 200 and b and b.get("token") and b.get("tipo") == "RESPONSAVEL", f"{st} {b}")
    t_resp = (b or {}).get("token")
    st, b = entrar(email_resp2)
    t_resp2 = (b or {}).get("token")
    st, b = entrar(email_pcd, "000000")
    checar("Senha errada: 401", st == 401, f"{st} {b}")
    st, b = chamar("GET", "/user/me", token=t_pcd)
    checar("/user/me com token: 200 e e-mail certo", st == 200 and b and b.get("email") == email_pcd, f"{st} {b}")
    st, b = chamar("GET", "/user/me")
    checar("/user/me sem token: 401", st == 401, f"{st} {b}")
    st, b = chamar("GET", "/user/me", token="token.invalido.qualquer")
    checar("Token inválido: 401 (e não 500)", st == 401, f"{st} {b}")
    if not (t_pcd and t_resp and t_resp2 and pcd_id):
        print("\nSem tokens/ids: não dá para continuar.")
        return 1

    secao("2) Sem vínculo (o app funciona assim por tempo indeterminado)")
    for nome, tok in (("PCD", t_pcd), ("Responsável", t_resp)):
        st, b = chamar("GET", "/api/vinculos", token=tok)
        checar(f"{nome}: lista de vínculos vazia", st == 200 and b == {"vinculos": []}, f"{st} {b}")

    secao("3) Regras de tipo de conta")
    st, b = chamar("POST", "/api/vinculos/codigos", token=t_resp)
    checar("Responsável NÃO gera código (403)", st == 403, f"{st} {b}")
    st, b = chamar("POST", "/api/vinculos/solicitar", {"codigo": "123456"}, token=t_pcd)
    checar("PCD NÃO usa código (403)", st == 403, f"{st} {b}")
    st, b = chamar("POST", "/api/vinculos/codigos")
    checar("Gerar código sem login: 401", st == 401, f"{st} {b}")

    secao("4) Código -> vínculo")
    st, b = chamar("POST", "/api/vinculos/codigos", token=t_pcd)   # corpo vazio, como o app faz
    codigo = (b or {}).get("codigo", "")
    checar("PCD gera código de 6 dígitos (201, validade 300 s)",
           st == 201 and len(codigo) == 6 and codigo.isdigit() and b.get("validadeSegundos") == 300, f"{st} {b}")
    st, b = chamar("POST", "/api/vinculos/solicitar", {"codigo": "000000" if codigo != "000000" else "111111"}, token=t_resp)
    checar("Código errado: 400", st == 400, f"{st} {b}")
    st, b = chamar("POST", "/api/vinculos/solicitar", {"codigo": codigo}, token=t_resp)
    checar("Responsável usa o código: 201, vínculo ATIVO com o PCD certo",
           st == 201 and b and b.get("status") == "ATIVO" and b.get("relacionadoId") == pcd_id and b.get("tipoRelacionado") == "PCD_VISUAL", f"{st} {b}")
    vinculo_id = (b or {}).get("id")
    st, b = chamar("POST", "/api/vinculos/solicitar", {"codigo": codigo}, token=t_resp2)
    checar("Código já usado não vale para outro (409)", st == 409, f"{st} {b}")
    st, b = chamar("GET", "/api/vinculos", token=t_pcd)
    checar("PCD vê 1 responsável ativo", st == 200 and len(b["vinculos"]) == 1 and b["vinculos"][0]["status"] == "ATIVO"
           and b["vinculos"][0]["tipoRelacionado"] == "RESPONSAVEL", f"{st} {b}")
    st, b = chamar("GET", "/api/vinculos", token=t_resp)
    checar("Responsável vê 1 pessoa vinculada", st == 200 and len(b["vinculos"]) == 1 and b["vinculos"][0]["relacionadoId"] == pcd_id, f"{st} {b}")
    st, b = chamar("GET", f"/api/vinculos/{vinculo_id}", token=t_resp2)
    checar("Terceiro não enxerga o vínculo (404)", st == 404, f"{st} {b}")

    secao("5) Dados do PCD protegidos pelo vínculo e pelas permissões")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/localizacao", token=t_resp)
    checar("Vinculado + permissão: 200 (disponivel=false, sem dados inventados)", st == 200 and b and b.get("disponivel") is False, f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/historico", token=t_resp)
    checar("Histórico não vem no padrão: 403", st == 403, f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/localizacao", token=t_resp2)
    checar("Responsável NÃO vinculado: 403", st == 403, f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id + 9999}/localizacao", token=t_resp)
    checar("Trocar o id na URL: 403", st == 403, f"{st} {b}")
    st, b = chamar("PUT", f"/api/vinculos/{vinculo_id}/permissoes", {"permissoes": ["HISTORICO_ROTAS"]}, token=t_pcd)
    checar("PCD altera as permissões (200)", st == 200 and b.get("permissoes") == ["HISTORICO_ROTAS"], f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/historico", token=t_resp)
    checar("Permissão nova vale na hora (histórico 200)", st == 200, f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/localizacao", token=t_resp)
    checar("Permissão retirada vale na hora (localização 403)", st == 403, f"{st} {b}")
    st, b = chamar("PUT", f"/api/vinculos/{vinculo_id}/permissoes", {"permissoes": ["LOCALIZACAO"]}, token=t_resp)
    checar("Responsável NÃO altera as próprias permissões (403)", st == 403, f"{st} {b}")

    secao("6) Revogação")
    st, b = chamar("DELETE", f"/api/vinculos/{vinculo_id}", token=t_pcd)
    checar("PCD encerra o vínculo (204)", st == 204, f"{st} {b}")
    st, b = chamar("GET", f"/api/responsavel/pcds/{pcd_id}/historico", token=t_resp)
    checar("Acesso do responsável bloqueado na hora (403)", st == 403, f"{st} {b}")
    st, b = chamar("GET", "/api/vinculos", token=t_resp)
    checar("Lista do responsável volta a ficar vazia", st == 200 and b == {"vinculos": []}, f"{st} {b}")
    st, b = entrar(email_pcd)
    checar("Contas continuam existindo (login do PCD ainda funciona)", st == 200, f"{st} {b}")

    if TESTAR_IA:
        import base64
        secao("7) Câmera + IA (POST /detect)")
        st, b = enviar_imagem("/detect", base64.b64decode(JPEG_MINIMO_B64), t_pcd)
        checar("/detect responde 200 com 'objects', 'guidance' e 'scene' (a IA está rodando)",
               st == 200 and isinstance(b, dict) and {"objects", "guidance", "scene"} <= set(b), f"{st} {b}")
        st, b = enviar_imagem("/detect", base64.b64decode(JPEG_MINIMO_B64), "token.invalido")
        checar("/detect sem login válido: 401", st == 401, f"{st} {b}")

    ok = sum(resultados)
    print(f"\n{ok}/{len(resultados)} verificações passaram.")
    if ok == len(resultados):
        print("Backend e contrato do app estão alinhados.")
        return 0
    print("Há falhas: veja as linhas FALHA acima (o trecho depois de '->' é o que o servidor respondeu).")
    return 1


if __name__ == "__main__":
    sys.exit(main())
