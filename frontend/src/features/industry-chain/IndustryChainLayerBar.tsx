import { INDUSTRY_CHAIN_LAYERS, industryChainLayerDefinition } from './industryChainLayers';
import type { IndustryChainLayer } from './industryChainTypes';

export function IndustryChainLayerBar({ activeLayer, onChange }: {
  activeLayer: IndustryChainLayer;
  onChange: (layer: IndustryChainLayer) => void;
}) {
  const active = industryChainLayerDefinition(activeLayer);
  return (
    <div className={`ic-layer-bar ic-layer-bar--${activeLayer.toLocaleLowerCase()}`}
      role="group" aria-label="产业专题图层">
      <div className="ic-layer-title"><span>Semantic lens</span><strong>专题图层</strong></div>
      <div className="ic-layer-options">
        {INDUSTRY_CHAIN_LAYERS.map((layer) => (
          <button type="button" key={layer.value} className={activeLayer === layer.value ? 'is-active' : ''}
            aria-pressed={activeLayer === layer.value} onClick={() => onChange(layer.value)}>
            <i aria-hidden="true" />
            <span><strong>{layer.label}</strong><small>{layer.hint}</small></span>
          </button>
        ))}
      </div>
      <div className="ic-layer-guide" aria-live="polite">
        <p><strong>{active.label}</strong><span>{active.description}</span></p>
        <div className="ic-layer-legend" aria-label={`${active.label}图例`}>
          {active.legend.map((item) => (
            <span className={`is-${item.tone}`} key={item.label}><i aria-hidden="true" />{item.label}</span>
          ))}
        </div>
      </div>
    </div>
  );
}
