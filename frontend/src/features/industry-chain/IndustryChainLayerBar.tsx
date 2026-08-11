import type { IndustryChainLayer } from './industryChainTypes';

const LAYERS: Array<{ value: IndustryChainLayer; label: string; hint: string }> = [
  { value: 'STRUCTURE', label: '产业结构', hint: '节点与价值流' },
  { value: 'VALUE', label: '价值分配', hint: '价值获取能力' },
  { value: 'BOTTLENECK', label: '产业瓶颈', hint: '关键卡点强度' },
  { value: 'TECHNOLOGY', label: '技术路线', hint: '技术成熟阶段' },
  { value: 'LOCALIZATION', label: '国产替代', hint: '本土供给水平' },
  { value: 'COMPANY', label: '公司生态', hint: '代表参与者' }
];

export function IndustryChainLayerBar({ activeLayer, onChange }: {
  activeLayer: IndustryChainLayer;
  onChange: (layer: IndustryChainLayer) => void;
}) {
  return (
    <div className={`ic-layer-bar ic-layer-bar--${activeLayer.toLocaleLowerCase()}`}
      role="group" aria-label="产业专题图层">
      <div className="ic-layer-title"><span>Semantic lens</span><strong>专题图层</strong></div>
      <div className="ic-layer-options">
        {LAYERS.map((layer) => (
          <button type="button" key={layer.value} className={activeLayer === layer.value ? 'is-active' : ''}
            aria-pressed={activeLayer === layer.value} onClick={() => onChange(layer.value)}>
            <i aria-hidden="true" />
            <span><strong>{layer.label}</strong><small>{layer.hint}</small></span>
          </button>
        ))}
      </div>
    </div>
  );
}
