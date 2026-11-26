#!/usr/bin/env python3
"""
Confere se um servidor OSRM calcula rotas de PEDESTRE de verdade (e não a rota de carro com o nome "foot").

Por que existe: router.project-osrm.org só tem o grafo de CARRO e ignora o segmento foot/bike da URL (devolve a
mesma rota de carro sem avisar). Não dá para confiar no nome do perfil na URL: é preciso comparar resultados.

Uso (só biblioteca padrão do Python 3; precisa de internet até o servidor):
  python verify_osrm_profile.py                                   # testa o padrão do WeSee (FOSSGIS /routed-foot)
  python verify_osrm_profile.py --base https://meu-osrm --path /route/v1/foot
Compara o servidor candidato com uma referência de CARRO. Se distância E duração forem iguais, é carro (reprovado).
Um trajeto a pé costuma levar várias vezes mais tempo que o de carro no mesmo par de pontos.
Só depois de "APROVADO" defina OSRM_WALKING_CONFIRMED=true no backend.
"""
import argparse, json, sys, urllib.request

# par de pontos em São Paulo (Av. Paulista -> Rua Afonso de Freitas), lon,lat
PAIR = "-46.6559,-23.5614;-46.6478,-23.5632"


def fetch(base, path, pair):
    url = f"{base.rstrip('/')}{path}/{pair}?overview=false&steps=false"
    req = urllib.request.Request(url, headers={"User-Agent": "WeSee-Backend/1.0 (verify_osrm_profile)"})
    with urllib.request.urlopen(req, timeout=20) as r:
        data = json.load(r)
    if data.get("code") != "Ok":
        raise RuntimeError(f"{url} -> code={data.get('code')}")
    route = data["routes"][0]
    return route["distance"], route["duration"]


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="https://routing.openstreetmap.de")
    ap.add_argument("--path", default="/routed-foot/route/v1/driving")
    ap.add_argument("--car-base", default="https://routing.openstreetmap.de")
    ap.add_argument("--car-path", default="/routed-car/route/v1/driving")
    ap.add_argument("--pair", default=PAIR)
    a = ap.parse_args(argv)
    try:
        cand = fetch(a.base, a.path, a.pair)
        car = fetch(a.car_base, a.car_path, a.pair)
    except Exception as e:  # rede, JSON inválido, servidor fora do ar
        print(f"NÃO FOI POSSÍVEL VERIFICAR: {e}")
        return 2
    print(f"candidato: {cand[0]:.0f} m, {cand[1]:.0f} s | referência (carro): {car[0]:.0f} m, {car[1]:.0f} s")
    if abs(cand[0] - car[0]) < 1 and abs(cand[1] - car[1]) < 1:
        print("REPROVADO: resultado idêntico ao de carro. NÃO use este servidor como rota a pé.")
        return 1
    if cand[1] < car[1]:
        print("SUSPEITO: a pé não deveria ser mais rápido que de carro. Confira o servidor/perfil antes de confirmar.")
        return 1
    print("APROVADO: o candidato difere do carro e é mais lento (compatível com pedestre). "
          "Lembre: isso não prova que cada trecho seja acessível ou seguro.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
