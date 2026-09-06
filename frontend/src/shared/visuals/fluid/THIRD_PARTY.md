# 流体算法来源

流体数值求解着色器改写自 PavelDoGreat/WebGL-Fluid-Simulation 的 `script.js`：
https://github.com/PavelDoGreat/WebGL-Fluid-Simulation

粒子平流参考 Volcomix/ink-drop 的 `src/shaders/particle.vert.ts`，本项目改为纹理 ping-pong 更新：
https://github.com/Volcomix/ink-drop

本项目的 React 生命周期、卡片分区、渲染调度、主题材质与质量管理是接入 FinScope 的实现；未复制 React Bits 或 Commons Clause 代码。

MIT License

Copyright (c) 2017 Pavel Dobryakov

Copyright (c) 2022 Sébastien Jalliffier Verne

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## 三维星场参考（2026-09-07）

参考 [Drei Stars](https://github.com/pmndrs/drei/blob/master/src/core/Stars.tsx) 的透视尺寸衰减与柔边星点，以及 [Three.js 官方粒子示例](https://github.com/mrdoob/three.js/blob/dev/examples/webgl_points_sprites.html) 的三维空间分布与鼠标视差。这里只借鉴技术思路，未复制其实现或图片资源，也未引入 React Three Fiber / Drei 依赖。星场的 GPU 深度循环、星点材质和星云着色器由本项目实现。
