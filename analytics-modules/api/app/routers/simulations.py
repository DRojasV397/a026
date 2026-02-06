"""
Router de simulacion de escenarios.
RF-05: Simulacion de escenarios financieros.
"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import Optional, List
from pydantic import BaseModel, Field

from app.database import get_db
from app.middleware.auth_middleware import get_current_user
from app.services.simulation_service import SimulationService

router = APIRouter(prefix="/simulation", tags=["Simulacion"])


# === Schemas ===

class CreateScenarioRequest(BaseModel):
    """Request para crear escenario."""
    nombre: str = Field(..., min_length=3, max_length=120)
    descripcion: Optional[str] = None
    basado_en_historico: bool = Field(default=True, description="Usar datos historicos como base (RN-05.02)")
    periodos: int = Field(default=6, ge=1, le=12, description="Periodos a simular")


class ModifyParameterItem(BaseModel):
    """Item para modificar un parametro."""
    parametro: str = Field(..., description="Nombre del parametro")
    valorActual: float = Field(..., description="Valor actual/modificado del parametro")
    valorBase: Optional[float] = Field(None, description="Valor base del parametro (opcional)")


class ModifyParametersRequest(BaseModel):
    """Request para modificar parametros."""
    parametros: List[ModifyParameterItem] = Field(..., min_length=1)


class RunSimulationRequest(BaseModel):
    """Request para ejecutar simulacion."""
    guardar_resultados: bool = Field(default=True, description="Guardar resultados en BD")


class CompareRequest(BaseModel):
    """Request para comparar escenarios."""
    escenario_ids: List[int] = Field(
        ...,
        min_length=2,
        max_length=5,
        description="IDs de escenarios a comparar (2-5)"
    )


class CloneScenarioRequest(BaseModel):
    """Request para clonar escenario."""
    nuevo_nombre: str = Field(..., min_length=3, max_length=120)


# === Endpoints ===

@router.post("/create", summary="Crear escenario")
async def create_scenario(
    request: CreateScenarioRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Crea un nuevo escenario de simulacion.

    RN-05.02: Basado en datos historicos reales.

    El escenario se crea con parametros base inicializados
    a partir de los datos historicos de ventas y compras.
    """
    service = SimulationService(db)
    result = service.create_scenario(
        nombre=request.nombre,
        descripcion=request.descripcion,
        basado_en_historico=request.basado_en_historico,
        periodos=request.periodos,
        creado_por=current_user.idUsuario
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al crear escenario")
        )

    return result


@router.put("/{id_escenario}/parameters", summary="Modificar parametros")
async def modify_parameters(
    id_escenario: int,
    request: ModifyParametersRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Modifica los parametros de un escenario.

    RF-05.01: Permite modificar precio, costo, demanda.
    RN-05.01: Variaciones no pueden exceder +/- 50%.

    Parametros disponibles:
    - variacion_precio: Cambio porcentual en precios
    - variacion_costo: Cambio porcentual en costos
    - variacion_demanda: Cambio porcentual en demanda
    """
    service = SimulationService(db)
    result = service.modify_parameters(
        id_escenario=id_escenario,
        parametros=[p.model_dump() for p in request.parametros]
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail={
                "message": result.get("error", "Error al modificar parametros"),
                "errores": result.get("errores", [])
            }
        )

    return result


@router.post("/{id_escenario}/run", summary="Ejecutar simulacion")
async def run_simulation(
    id_escenario: int,
    request: RunSimulationRequest = RunSimulationRequest(),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Ejecuta la simulacion de un escenario.

    RF-05.02: Proyecta impacto financiero.
    RN-05.04: Los resultados son informativos.

    Calcula para cada periodo:
    - Ingresos simulados
    - Costos simulados
    - Utilidad bruta
    - Margen bruto

    Incluye comparacion con valores base y porcentajes de cambio.
    """
    service = SimulationService(db)
    result = service.run_simulation(
        id_escenario=id_escenario,
        guardar_resultados=request.guardar_resultados
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al ejecutar simulacion")
        )

    return result


@router.get("/{id_escenario}/results", summary="Obtener resultados")
async def get_scenario_results(
    id_escenario: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene los resultados de un escenario ejecutado.

    Retorna:
    - Datos del escenario
    - Parametros configurados
    - Resultados por periodo e indicador
    """
    service = SimulationService(db)
    result = service.get_scenario(id_escenario)

    if not result.get("success"):
        raise HTTPException(
            status_code=404,
            detail=result.get("error", "Escenario no encontrado")
        )

    return result


@router.post("/compare", summary="Comparar escenarios")
async def compare_scenarios(
    request: CompareRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Compara multiples escenarios.

    RF-05.03: Permite comparacion de escenarios.
    RN-05.03: Maximo 5 escenarios simultaneos.

    Retorna:
    - Comparacion por indicador y periodo
    - Mejor y peor escenario por metrica
    - Resumen consolidado
    """
    service = SimulationService(db)
    result = service.compare_scenarios(request.escenario_ids)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al comparar escenarios")
        )

    return result


@router.get("/scenarios", summary="Listar escenarios")
async def list_scenarios(
    solo_activos: bool = Query(False, description="Solo escenarios activos"),
    usuario_id: Optional[int] = Query(None, description="Filtrar por usuario"),
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Lista todos los escenarios disponibles.

    Incluye resumen con:
    - Numero de parametros
    - Numero de resultados
    - Totales simulados
    """
    service = SimulationService(db)
    result = service.list_scenarios(
        usuario_id=usuario_id,
        solo_activos=solo_activos
    )

    return result


@router.post("/{id_escenario}/save", summary="Guardar escenario")
async def save_scenario(
    id_escenario: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Guarda/confirma un escenario ejecutado.

    Cambia el estado a 'Ejecutado' si tiene resultados.
    """
    service = SimulationService(db)
    result = service.save_scenario(id_escenario)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al guardar escenario")
        )

    return result


@router.post("/{id_escenario}/archive", summary="Archivar escenario")
async def archive_scenario(
    id_escenario: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Archiva un escenario.

    Los escenarios archivados no pueden ser modificados ni ejecutados.
    """
    service = SimulationService(db)
    result = service.archive_scenario(id_escenario)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al archivar escenario")
        )

    return result


@router.delete("/{id_escenario}", summary="Eliminar escenario")
async def delete_scenario(
    id_escenario: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Elimina un escenario y todos sus datos relacionados.

    Esta accion no se puede deshacer.
    """
    service = SimulationService(db)
    result = service.delete_scenario(id_escenario)

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al eliminar escenario")
        )

    return result


@router.post("/{id_escenario}/clone", summary="Clonar escenario")
async def clone_scenario(
    id_escenario: int,
    request: CloneScenarioRequest,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Clona un escenario existente.

    Crea un nuevo escenario con:
    - Los mismos parametros del original
    - Estado 'Activo'
    - Sin resultados (debe ejecutarse)
    """
    service = SimulationService(db)
    result = service.clone_scenario(
        id_escenario=id_escenario,
        nuevo_nombre=request.nuevo_nombre,
        creado_por=current_user.idUsuario
    )

    if not result.get("success"):
        raise HTTPException(
            status_code=400,
            detail=result.get("error", "Error al clonar escenario")
        )

    return result


@router.get("/{id_escenario}", summary="Obtener escenario")
async def get_scenario(
    id_escenario: int,
    db: Session = Depends(get_db),
    current_user: dict = Depends(get_current_user)
):
    """
    Obtiene un escenario por ID.

    Incluye parametros y resultados.
    """
    service = SimulationService(db)
    result = service.get_scenario(id_escenario)

    if not result.get("success"):
        raise HTTPException(
            status_code=404,
            detail=result.get("error", "Escenario no encontrado")
        )

    return result
