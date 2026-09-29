N.update({'okra':(.33,.019,.075,.002),'cornflour':(3.81,.003,.91,.001),'cabbage':(.25,.013,.058,.001),
 'mozzarella':(3.0,.22,.024,.22),'pasta':(3.55,.125,.72,.015),'ricefl':(3.66,.06,.8,.014),'sesame':(5.73,.177,.23,.5),
 'gochujang':(2.0,.04,.4,.02),'soysauce':(.53,.08,.05,0),'butter':(7.17,.009,.001,.81),'herbs':(.3,.03,.04,.005),'garlic':(1.49,.064,.33,.005)})
REEL={}
def reel(key,name,url,ings,veg=False): add(key,name,ings,'From your reel',veg=veg); REEL[key]=url
reel('okra','Crispy okra fries + buttermilk','https://www.instagram.com/reel/DcqWx_tA5Xs/',{'okra':150,'cornflour':10,'oil':2,'spices':3,'curd_lf':75},veg=True)
reel('dumplings','Cabbage chicken dumplings (5 rolls)','https://www.instagram.com/reel/DdEZlGMNdP2/',{'chicken':200,'egg_pc':2,'onion':50,'cabbage':150,'oil':5,'garlic':10,'spices':5})
reel('malai','Malai chicken + rice + salad','https://www.instagram.com/reel/DZxgQMDTxro/',{'chicken':150,'curd_lf':15,'garlic':5,'mozzarella':15,'herbs':10,'rice_raw':35,'cucumber':100})
reel('tikkabowl','Paneer tikka rice bowl','https://www.instagram.com/reel/DTXtM9NEXCa/',{'paneer_lf':200,'capsicum':120,'onion':100,'curd_lf':50,'oil':5,'rice_raw':50,'spices':10},veg=True)
reel('cgfr','Chilli garlic paneer fried rice','https://www.instagram.com/reel/DXGAJqODMht/',{'paneer_lf':150,'oil':5,'garlic':15,'onion':50,'veg_mix':150,'rice_raw':50,'spices':10},veg=True)
reel('kabab','Air-fryer chicken kabab + 2 chapatis + salad','https://www.instagram.com/reel/Dak2VitzaFc/',{'chicken':150,'egg_pc':.3,'cornflour':3,'spices':5,'atta':60,'cucumber':100})
reel('greensoya','Green soya rice (hariyali / nati style) + curd','https://www.instagram.com/reel/Dc7uZfKTk1s/',{'soya':50,'rice_raw':60,'oil':7,'onion':50,'herbs':40,'garlic':10,'spices':5,'curd_lf':100},veg=True)
reel('koreansoya','Korean-style crispy soya + stir-fried veg','https://www.instagram.com/reel/Dba2XvsCgD2/',{'soya':60,'ricefl':10,'cornflour':10,'soysauce':10,'oil':5,'onion':40,'garlic':10,'gochujang':15,'sesame':3,'veg_mix':150},veg=True)
reel('eggroast','Egg ghee roast (light) + 1 chapati','https://www.instagram.com/reel/DcIfLPAyCv4/',{'egg_pc':3,'eggwhite_pc':2,'ghee':5,'cashew':5,'spices':15,'tamarind':5,'garlic':10,'atta':30})
reel('pasta','High-protein paneer pasta','https://www.instagram.com/reel/DcD-L6Tz3bz/',{'pasta':60,'paneer_lf':200,'milk_dt':100,'onion':50,'tomato':100,'garlic':15,'spices':5},veg=True)
reel('corichicken','Coriander butter garlic chicken + 1 chapati','https://www.instagram.com/reel/DbyCyIizCEp/',{'chicken':180,'paneer_lf':50,'cornflour':5,'butter':5,'oil':2,'onion':40,'garlic':10,'herbs':30,'atta':30})
EXTRA_REEL={'tandoori':'https://www.instagram.com/reel/Dakh0I3N1p2/','greensoya2':'https://www.instagram.com/reel/DcqoMeGvrF8/'}
PLAN_B = [
 ('Monday','Training',False,['banana','shake','bhurji',('malai','sambar'),'okra','dumplings']),
 ('Tuesday','Training · Veg day',True,['banana','shake','chilla',('pasta','buttermilk'),'sprouts','koreansoya']),
 ('Wednesday','Training',False,['banana','shake','idli','corichicken','fruitcurd','eggroast']),
 ('Thursday','Training · Veg day',True,['banana','shake','pesarattu_p','greensoya','fruitcurd','palak']),
 ('Friday','Training',False,['banana','shake','oatsbowl','tandoori','eggs2','cgfr']),
 ('Saturday','Training (optional)',False,['banana','shake','pesarattu_e','tikkabowl','sprouts','kabab']),
 ('Sunday','Rest day',False,['oatswhey',('malai','sambar'),'eggs2','koreansoya']),
]
