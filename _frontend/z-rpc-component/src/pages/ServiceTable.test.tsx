/**
 * `ServiceTable`（运维工作台 · 服务注册中心 · 服务列表）列绑定测试。
 *
 * 为什么要有这支测试：`GET /api/naming/listServices` 在当前库里返 `data:[]`（实测 2026-09-26 01:2x，
 * 带 cookie 的 200 + `{"data":[],"success":true,"code":200}`），**页面一行都渲染不出来** ⇒
 * 列绑定错了在浏览器里完全看不出来（普查只会读成"接口通·库空"）。
 * 而 `ZNamingController#listServices` 不返回实体，是**手工拼的 HashMap**，键集合和实体字段不一致：
 *   实发：serviceName / group / namespace / clusters / healthyInstanceCount / totalInstanceCount / status
 *   实体有但没被 put 进 map：clusterMap、healthCheckMode
 * 旧代码读的正是 `r.clusterMap`（恒 undefined）与 `healthCheckMode`（恒 undefined）⇒
 * 一旦注册表里有服务，"集群数 / 实例数"会**齐刷刷显示 0**，看着像"服务没实例"。
 * 这里用与后端逐键对齐的假行把这条钉住（**不接进任何页面，只喂组件**）。
 */
import {beforeEach, describe, expect, it, vi} from "vitest";
import {render, screen, waitFor} from "@testing-library/react";

vi.mock("react-router-dom", () => ({useNavigate: () => vi.fn()}));

import * as rpcApiModule from "../services/api";
import ServiceTable from "./ServiceTable";

/** 与 `ZNamingController#listServices` 的 `map.put(...)` 逐键对齐 */
const REAL_ROW = {
    serviceName: "z-demo-provider",
    group: "DEFAULT_GROUP",
    namespace: "public",
    clusters: JSON.stringify({default: [{ip: "10.0.0.1", port: 20880}, {ip: "10.0.0.2", port: 20880}]}),
    healthyInstanceCount: 1,
    totalInstanceCount: 2,
    status: "异常",
};

const listSpy = vi.spyOn(rpcApiModule.rpcApi, "listServices");

/**
 * 取某一行的 `<td>` 列表。
 * ⚠️ **jsdom 不实现 `innerText`** —— 实测 `typeof td.innerText === 'undefined'`（而 `td` 本身在、
 * `closest("tr")` 也取对了，8 个 td 一个不少），所以取单元格文本一律用 `textContent`。
 * 这一行的第一版断言写成 `innerText.trim()`，报错是 `Cannot read properties of undefined (reading 'trim')`，
 * 看着像"组件没渲染出列"—— 差一步就开出一张假的前端缺陷卡。
 * 数据行只认 `.ant-table-row`（真数据行才有这个 class），并要求首列文本精确等于服务名。
 */
const rowFor = (container: HTMLElement, serviceName: string) => {
    const hit = Array.from(container.querySelectorAll("tr.ant-table-row"))
        .find((r) => (r.querySelector("td")?.textContent || "").trim() === serviceName);
    if (!hit) throw new Error(`没找到服务行 ${serviceName}（数据行共 `
        + `${container.querySelectorAll("tr.ant-table-row").length} 条）`);
    return hit as HTMLElement;
};

/** 列序：0 服务名 1 分组 2 命名空间 **3 集群数 4 实例数 5 健康实例 6 状态** 7 操作 */
const cell = (container: HTMLElement, serviceName: string, idx: number) => {
    const td = rowFor(container, serviceName).querySelectorAll("td")[idx] as HTMLElement | undefined;
    if (!td) throw new Error(`第 ${idx} 列不存在，该行只有 `
        + `${rowFor(container, serviceName).querySelectorAll("td").length} 个 td`);
    return td.textContent?.trim() ?? '';
};

beforeEach(() => {
    vi.clearAllMocks();
});

describe("ServiceTable 列绑定", () => {
    it("按后端实发的键渲染集群数 / 实例数 / 健康实例 / 状态", async () => {
        listSpy.mockResolvedValue([REAL_ROW]);
        const {container} = render(<ServiceTable/>);

        await waitFor(() => expect(screen.getByText("z-demo-provider")).toBeInTheDocument());
        expect(cell(container, "z-demo-provider", 3)).toBe("1");       // clusters JSON 里 1 个集群
        expect(cell(container, "z-demo-provider", 4)).toBe("2");       // totalInstanceCount
        expect(cell(container, "z-demo-provider", 5)).toBe("1/2");     // healthy/total
        expect(cell(container, "z-demo-provider", 6)).toBe("异常");
    });

    it("反向锁： decoy 键 clusterMap / healthCheckMode 不参与渲染（读回旧键就红）", async () => {
        listSpy.mockResolvedValue([{
            ...REAL_ROW,
            // 3 个集群 + 一个健康检查模式，**都不在该接口实发的键集合里**：
            // 若有人把 render 改回 `r.clusterMap` / `r.healthCheckMode`，这三条断言立刻翻红。
            clusterMap: JSON.stringify({a: [{}], b: [{}], c: [{}]}),
            healthCheckMode: "server",
        }]);
        const {container} = render(<ServiceTable/>);

        await waitFor(() => expect(screen.getByText("z-demo-provider")).toBeInTheDocument());
        expect(cell(container, "z-demo-provider", 3)).toBe("1");       // 读 decoy 会得 3
        expect(cell(container, "z-demo-provider", 4)).toBe("2");       // 按 decoy 数会得 3
        expect(cell(container, "z-demo-provider", 6)).toBe("异常");    // 读 healthCheckMode 会得 server
    });

    it("clusters 解析失败要显式说「解析失败」，不能当成 0 集群", async () => {
        listSpy.mockResolvedValue([{...REAL_ROW, clusters: "{not json"}]);
        render(<ServiceTable/>);

        await waitFor(() => expect(screen.getByText("解析失败")).toBeInTheDocument());
    });

    it("缺计数字段时显示 '-'，不能显示 0（0 会被读成「这个服务没有实例」）", async () => {
        listSpy.mockResolvedValue([{
            serviceName: "z-no-count",
            group: "DEFAULT_GROUP",
            namespace: "public",
            clusters: JSON.stringify({default: []}),
        }]);
        const {container} = render(<ServiceTable/>);

        await waitFor(() => expect(screen.getByText("z-no-count")).toBeInTheDocument());
        expect(cell(container, "z-no-count", 4)).toBe("-");
        expect(cell(container, "z-no-count", 5)).toBe("-");
        expect(cell(container, "z-no-count", 6)).toBe("-");
    });

    it("接口报错时摆出真因，不退成「注册表为空」", async () => {
        listSpy.mockRejectedValue(new Error("naming 表不存在"));
        render(<ServiceTable/>);

        await waitFor(() => expect(screen.getByText(/服务列表读取失败/)).toBeInTheDocument());
        expect(screen.getByText(/naming 表不存在/)).toBeInTheDocument();
        expect(screen.queryByText("注册表为空")).not.toBeInTheDocument();
    });
});
