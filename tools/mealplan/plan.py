# Nutrition per unit (kcal, P, C, F). Units: g unless noted.
N = {
 'oats':(3.80,.13,.67,.07), 'milk_dt':(0.46,.033,.048,.015), 'banana_pc':(105,1.3,27,.4),
 'whey_scoop':(153,25,9.06,1.88), 'egg_pc':(72,6.3,.4,4.8), 'eggwhite_pc':(17,3.6,.2,.1),
 'idli_batter':(1.16,.04,.24,.004), 'toor':(3.43,.223,.6,.017), 'sambar_veg':(.35,.015,.06,.002),
 'tamarind':(2.4,.03,.6,.005), 'oil':(9,0,0,1), 'ghee':(9,0,0,1), 'onion':(.4,.011,.09,.001),
 'tomato':(.18,.009,.039,.002), 'moong_whole':(3.47,.24,.63,.012), 'moong_yellow':(3.48,.245,.6,.012),
 'paneer':(2.65,.18,.012,.2), 'atta':(3.4,.12,.72,.017), 'curd_lf':(.6,.031,.044,.03),
 'greek':(.73,.10,.04,.02), 'pomegranate':(.83,.017,.19,.012), 'papaya':(.43,.005,.11,.003),
 'chicken':(1.2,.225,0,.026), 'fish':(1.0,.19,0,.02), 'prawn':(.85,.20,0,.005),
 'rice_raw':(3.6,.07,.79,.006), 'basmati_raw':(3.58,.072,.78,.006), 'veg_mix':(.3,.02,.06,.002),
 'cucumber':(.15,.007,.036,.001), 'roasted_chana':(3.69,.2,.56,.05), 'kala_chana':(3.6,.171,.609,.053),
 'apple_pc':(95,.5,25,.3), 'guava_pc':(68,2.6,14,1), 'makhana':(3.47,.097,.77,.001),
 'spices':(1,0,0,0), 'cashew':(5.53,.18,.3,.44), 'peas':(.81,.054,.145,.004),
 'soya':(3.45,.52,.33,.005), 'spinach':(.23,.029,.036,.004), 'capsicum':(.3,.01,.06,.003),
}
def mac(ings):
    k=p=c=f=0
    for n,q in ings.items():
        a=N[n]; k+=a[0]*q; p+=a[1]*q; c+=a[2]*q; f+=a[3]*q
    return (round(k),round(p,1),round(c),round(f,1))

R = {}  # name -> (kcal,P,C,F, ingredients, source)
def add(key,name,ings,src='New',fixed=None,veg=False,slot=''):
    m = fixed if fixed else mac(ings)
    R[key]=dict(name=name,m=m,ings=ings,src=src,veg=veg,slot=slot)

# basics
add('banana','Pre-workout: banana + black coffee',{'banana_pc':1},'Basic',veg=True)
add('shake','Post-workout whey shake (1 scoop in 250 ml water)',{'whey_scoop':1},'Your whey',veg=True)
add('buttermilk','Glass of buttermilk (masala majjiga/chaas)',{'curd_lf':75},'Basic',veg=True)
add('sambar','Protein sambar (1 bowl)',{'toor':30,'sambar_veg':100,'tamarind':5,'oil':3,'onion':20,'tomato':30,'spices':5},veg=True)
# breakfasts
add('idli','Idli + protein sambar + boiled egg',{'idli_batter':150,'toor':30,'sambar_veg':100,'tamarind':5,'oil':3,'onion':20,'tomato':30,'spices':5,'egg_pc':1})
add('pesarattu_p','Paneer pesarattu + ginger-tomato chutney',{'moong_whole':60,'oil':8,'tomato':100,'spices':5,'paneer':40},veg=True)
add('pesarattu_e','Pesarattu + chutney + boiled egg',{'moong_whole':60,'oil':8,'tomato':100,'spices':5,'egg_pc':1})
add('bhurji','Egg bhurji (2 eggs + 3 whites) + 1 chapati',{'egg_pc':2,'eggwhite_pc':3,'onion':50,'tomato':50,'oil':3,'atta':30})
add('chilla','Moong dal paneer chilla + curd',{'moong_yellow':60,'paneer':50,'onion':20,'oil':5,'curd_lf':100},'Handbook p.40 (+100 g curd)',fixed=(381,27.1,44,10),veg=True)
add('oatsbowl','Oats protein breakfast bowl',{'oats':65,'greek':100,'pomegranate':100,'egg_pc':1},'Handbook p.7',fixed=(477,25,66,13))
add('oatswhey','Oats & whey power bowl (full scoop) + papaya',{'oats':50,'milk_dt':200,'whey_scoop':1,'papaya':150},'Handbook p.8, full scoop of your whey',veg=True)
# handbook mains (handbook numbers)
H=lambda key,name,page,m,ings,veg=False: add(key,name,ings,f'Handbook p.{page}',fixed=m,veg=veg)
H('chx_rice','High-protein chicken rice bowl',11,(575,55,60,12),{'chicken':150,'rice_raw':50,'veg_mix':250,'oil':5})
H('chx_chapati','Chapati chicken bowl',12,(623,59,64,14),{'chicken':150,'atta':60,'veg_mix':250,'oil':5})
H('tandoori','Tandoori-style grilled chicken + rice',13,(490,44,46,11),{'chicken':150,'curd_lf':100,'oil':5,'rice_raw':50})
H('tikkamasala','Chicken tikka masala + rice',15,(477,41,43,15),{'chicken':150,'onion':70,'tomato':150,'curd_lf':30,'cashew':10,'oil':5,'rice_raw':35})
H('keema','Chicken keema + rice',16,(433,39,44,11),{'chicken':150,'onion':70,'tomato':100,'peas':50,'oil':5,'rice_raw':35})
H('fishtikka','Air fryer fish tikka + rice',19,(445,45,42,9),{'fish':150,'curd_lf':100,'capsicum':75,'onion':75,'oil':5,'rice_raw':35})
add('fishbowl','Grilled fish bowl (100 g rice)',{'fish':150,'rice_raw':35,'veg_mix':250,'oil':5},'Handbook p.18 (100 g rice)',fixed=(430,43,46,9))
H('palak','Palak paneer + rice',26,(481,47,53,10),{'paneer_lf':200,'spinach':200,'onion':70,'tomato':100,'oil':5,'rice_raw':35},veg=True)
H('kadai','Kadai paneer + rice',29,(449,42,51,10),{'paneer_lf':200,'capsicum':150,'onion':70,'tomato':100,'oil':5,'rice_raw':35},veg=True)
H('soyacurry','Soya chunks curry + rice',33,(541,42,77,6),{'soya':70,'onion':70,'tomato':100,'oil':5,'rice_raw':50},veg=True)
H('soyakeema','Soya keema + rice',35,(460,38,63,6),{'soya':60,'onion':70,'tomato':100,'peas':50,'oil':5,'rice_raw':35},veg=True)
H('eggbites','Air fryer egg muffin bites',41,(371,34,4,25),{'egg_pc':4,'spinach':50})
# new South Indian mains
add('pepperfry','Chicken pepper fry (Chettinad-style) + rice + salad',{'chicken':150,'onion':70,'tomato':50,'oil':5,'spices':10,'rice_raw':50,'cucumber':100})
add('pulusu','Andhra fish pulusu + rice',{'fish':150,'tamarind':10,'onion':50,'tomato':100,'oil':5,'spices':10,'rice_raw':50})
add('prawn','Prawn masala (Kerala-style roast) + 1½ chapati',{'prawn':150,'onion':70,'tomato':100,'oil':5,'spices':10,'atta':45})
add('biryani','Lean chicken biryani + cucumber raita',{'chicken':150,'basmati_raw':60,'curd_lf':150,'onion':50,'ghee':7,'spices':10,'cucumber':50})
# snacks
add('sprouts','Moong sprouts chaat',{'moong_whole':40,'onion':20,'tomato':30},veg=True)
add('chana_bm','Roasted chana + buttermilk',{'roasted_chana':30,'curd_lf':75},veg=True)
add('fruitcurd','Apple + Greek yogurt / hung curd',{'apple_pc':1,'greek':100},veg=True)
add('kalachana','Kala chana chaat',{'kala_chana':40,'onion':20,'tomato':30},veg=True)
add('eggs2','2 boiled eggs + cucumber',{'egg_pc':2,'cucumber':100})
add('makhana','Roasted makhana + buttermilk',{'makhana':25,'curd_lf':75,'oil':2},veg=True)
N['paneer_lf']=(1.08,.18,.02,.03)

PLAN = [
 ('Monday','Training',False,['banana','shake','idli','chx_rice','sprouts','fishtikka']),
 ('Tuesday','Training · Veg day',True,['banana','shake','pesarattu_p','soyacurry','chana_bm','palak']),
 ('Wednesday','Training',False,['banana','shake','bhurji',('pepperfry','sambar'),'fruitcurd','keema']),
 ('Thursday','Training · Veg day',True,['banana','shake','chilla',('kadai','sambar'),'kalachana','soyakeema']),
 ('Friday','Training',False,['banana','shake','oatsbowl',('pulusu','buttermilk'),'eggs2','tandoori']),
 ('Saturday','Training (optional)',False,['banana','shake','pesarattu_e','chx_chapati','makhana','prawn']),
 ('Sunday','Rest day',False,['oatswhey','biryani','eggs2','fishbowl']),
]
import json
out=[]
for day,kind,veg,items in PLAN:
    tot=[0,0,0,0]; rows=[]
    for it in items:
        keys = it if isinstance(it,tuple) else (it,)
        for k in keys:
            m=R[k]['m']; tot=[a+b for a,b in zip(tot,m)]
        rows.append(keys)
    out.append((day,kind,veg,rows,tot))
    print(f"{day:10} {kind:22} kcal {tot[0]:5.0f}  P {tot[1]:5.1f}  C {tot[2]:4.0f}  F {tot[3]:4.1f}")
avg=sum(o[4][0] for o in out)/7; avgp=sum(o[4][1] for o in out)/7
print('avg',round(avg),round(avgp,1))
for k,v in R.items(): print(k, v['m'], v['src'])
json.dump({'R':R,'out':out},open('plan.json','w'),default=list)
# groceries
G={}
for day,kind,veg,rows,tot in out:
    for keys in rows:
        for k in keys:
            for n,q in R[k]['ings'].items(): G[n]=G.get(n,0)+q
print(json.dumps({k:round(v) for k,v in sorted(G.items())},indent=0))
