package com.finscope.domain.news;

import java.util.regex.Pattern;

/** 雷达和实时资讯共用的标题重要性口径。 */
public final class NewsContentImportance {
    private static final Pattern POLICY = Pattern.compile("(央行|人民银行|美联储|国务院|证监会|政府|国会).{0,30}(降准|降息|加息|下调.*准备金率|维持.*利率|出口管制|批准|通过|实施|禁止|取消限制)");
    private static final Pattern EARNINGS = Pattern.compile("业绩预告|季度业绩|季度.*(收入|营收|净利润)|净利润.*同比|营收.*同比|预增|预亏|扭亏");
    private static final Pattern PUBLIC_POLICY = Pattern.compile("(财政部|商务部|国务院|发改委|全国|央行|人民银行).*(发布|公布|出台|实施|调整|批准|取消|修订)|停火|重大事故|立案调查|留置|终止.*合资");
    private static final Pattern CONTRACT_TERMINATION = Pattern.compile("(终止|解除).{0,24}(重大|[0-9.]+亿).{0,18}(合同|协议)|重大(合同|协议).{0,8}(终止|解除)");
    private static final Pattern DEVELOPMENT = Pattern.compile("降准|降息|加息|出口管制|正式实施|获批|业绩预告|重大资产重组|收购|中标|新一代|新模型|新款|量产|突破|投产");
    private static final Pattern ROUTINE = Pattern.compile("^预告[:：]|(定于|将于).*新闻发布会|投资日历|大事提醒|(召开|举行|召开情况).{0,20}业绩说明会|业绩说明会.{0,15}(召开|通知|公告|纪要)|董事会.*(会议|决议)|股东.*(通知|决议)|融资余额|融资买入|龙虎榜|前十名.*持股|前十名股东|律师事务所|法律意见|核查意见|投资者关系活动记录表|授予.*(结果|登记)|归属名单|收购报告书摘要|闲置募集资金");
    private static final Pattern NO_BREAKTHROUGH = Pattern.compile("(未现|未取得|未能取得|未能实现|没有取得)突破");
    private static final Pattern UNCERTAIN = Pattern.compile("传闻|网传|未经证实|据传|有望|或将|可能会|考虑|研究是否|拟议|否认|尚未|不会|未宣布|是否|还.*吗|[？?]");

    private NewsContentImportance() { }

    public static boolean uncertain(String title) {
        return UNCERTAIN.matcher(title).find();
    }

    public static int score(String title) {
        int score = 35;
        if (DEVELOPMENT.matcher(NO_BREAKTHROUGH.matcher(title).replaceAll("")).find()) {
            score = 70;
        }
        if (EARNINGS.matcher(title).find()) {
            score = Math.max(score, 75);
        }
        if (PUBLIC_POLICY.matcher(title).find()) {
            score = Math.max(score, 80);
        }
        if (CONTRACT_TERMINATION.matcher(title).find()) {
            score = Math.max(score, 85);
        }
        if (POLICY.matcher(title).find()) {
            score = 95;
        }
        if (ROUTINE.matcher(title).find()) {
            score = Math.min(score, 10);
        }
        if (uncertain(title)) {
            score = Math.min(score, 40);
        }
        return score;
    }
}
